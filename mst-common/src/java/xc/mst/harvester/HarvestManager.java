/**
 * Copyright (c) 2009 eXtensible Catalog Organization
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the MIT/X11 license. The text of the
 * license can be found at http://www.opensource.org/licenses/mit-license.php and copy of the license can be found on the project
 * website http://www.extensiblecatalog.org/.
 *
 */
package xc.mst.harvester;

import gnu.trove.TLongByteHashMap;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.httpclient.HttpException;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import xc.mst.bo.harvest.Harvest;
import xc.mst.bo.harvest.HarvestSchedule;
import xc.mst.bo.harvest.HarvestScheduleStep;
import xc.mst.bo.provider.Format;
import xc.mst.bo.provider.Provider;
import xc.mst.bo.provider.Set;
import xc.mst.bo.record.Record;
import xc.mst.bo.record.RecordCounts;
import xc.mst.cache.DynKeyLongMap;
import xc.mst.constants.Constants;
import xc.mst.constants.Status;
import xc.mst.dao.DataException;
import xc.mst.dao.DatabaseConfigException;
import xc.mst.email.Emailer;
import xc.mst.scheduling.WorkerThread;
import xc.mst.utils.LogWriter;
import xc.mst.utils.MSTConfiguration;
import xc.mst.utils.TimingLogger;
import xc.mst.utils.XmlHelper;

public class HarvestManager extends WorkerThread {

    /**
     * A reference to the logger which writes to the HarvestIn log file
     */
    private static Logger log = Logger.getLogger("harvestIn");
    private static Logger LOG = Logger.getLogger(HarvestManager.class);

    protected static DateTimeFormatter UTC_SECOND_FORMATTER = null;
    protected static DateTimeFormatter UTC_DAY_FORMATTER = null;
    static {
        UTC_SECOND_FORMATTER = ISODateTimeFormat.dateTime();
        UTC_SECOND_FORMATTER = UTC_SECOND_FORMATTER.withZone(DateTimeZone.UTC);

        UTC_DAY_FORMATTER = ISODateTimeFormat.date();
        UTC_DAY_FORMATTER = UTC_DAY_FORMATTER.withZone(DateTimeZone.UTC);
    }
    // Map<MostSigToken, ListOfAllOaoIdsThatHaveToken<EntireOaiId, recordId>>
    protected boolean cacheSetup = false;
    protected DynKeyLongMap oaiIdCache = new DynKeyLongMap();
    protected TLongByteHashMap previousStatuses = new TLongByteHashMap();
    
    protected static int LARGE_HARVEST_THRESHOLD_DEFAULT = 10000;
    protected int largeHarvestThreshold = LARGE_HARVEST_THRESHOLD_DEFAULT;

    // The is public and static simply for the MockHarvestTest
    public static String lastOaiRequest = null;
    protected HarvestSchedule harvestSchedule = null;
    protected List<HarvestScheduleStep> harvestScheduleSteps = null;
    protected boolean hssFirstTime = true;
    protected int harvestScheduleStepIndex = 0;
    protected Harvest currentHarvest = null;
    protected Date startDate = null;
    protected String resumptionToken = null;
    protected int requestsSent4Step = 0;
    protected long startTime = 0;

    protected long recordsProcessedThisRun = 0l;
    protected long records2ProcessThisRun = 0l;

    public String printDateTime(Date d) {
        String s = UTC_SECOND_FORMATTER.print(d.getTime());
        s = s.substring(0, s.length() - 5) + "Z";
        return s;
    }

    public String printDate(Date d) {
        String s = UTC_DAY_FORMATTER.print(d.getTime());
        return s;
    }

    /**
     * The policy for tracking deleted records that the OAI repository uses (either DELETED_RECORD_NO, DELETED_RECORD_TRANSIENT, or DELETED_RECORD_PERSISTENT)
     */
    protected int deletedRecord = -1;

    protected Emailer mailer = (Emailer) MSTConfiguration.getInstance().getBean("Emailer");

    public String getName() {
        if (harvestSchedule != null && harvestSchedule.getProvider() != null) {
            return "harvest-" + harvestSchedule.getProvider().getName();
        } else {
            return "harvest";
        }
    }

    public String getDetailedStatus() {
        return "processed " + this.recordsProcessedThisRun + " of " + this.records2ProcessThisRun;
    }

    public void setHarvestSchedule(HarvestSchedule harvestSchedule) {
        this.harvestSchedule = harvestSchedule;
    }

    public void setup() {

        try {
            hssFirstTime = true;
            this.resumptionToken = null;
            startTime = new Date().getTime();
            // BDA - I added this check for 0 becuase the initialization of HarvestSchedule.steps creates a new
            // list of size zero. The DAO which creates the harvestSchedule doesn't inject steps into it. So
            // there's really no other way to tell.
            if (harvestSchedule.getSteps() == null || harvestSchedule.getSteps().size() == 0) {
                harvestScheduleSteps = getHarvestScheduleStepDAO().getStepsForSchedule(harvestSchedule.getId());
            } else {
                harvestScheduleSteps = harvestSchedule.getSteps();
            }
            harvestScheduleStepIndex = 0;
            repo = getRepositoryService().getRepository(harvestSchedule.getProvider());

            String strLHT = MSTConfiguration.getInstance().getProperty(Constants.CONFIG_LARGE_HARVEST_THRESHOLD);
            largeHarvestThreshold = LARGE_HARVEST_THRESHOLD_DEFAULT;
            if (strLHT != null) {
                try {
                	largeHarvestThreshold = Integer.parseInt(strLHT);
                } catch (NumberFormatException e) {
                	largeHarvestThreshold = LARGE_HARVEST_THRESHOLD_DEFAULT;
                }
            }
            // no longer set up cache for all harvests; only do so for "large" ones
            //setupCache();

            this.currentHarvest = getScheduleService().getHarvest(harvestSchedule);
            this.incomingRecordCounts = new RecordCounts(this.currentHarvest.getEndTime(), RecordCounts.INCOMING);
        } catch (DatabaseConfigException e) {
            getUtil().throwIt(e);
        }
    }

    private void setupCache() {
    	if (cacheSetup) return; // one-time event only
        oaiIdCache.clear();
        previousStatuses.clear();
        TimingLogger.outputMemory();
        getRepositoryDAO().populateHarvestCache(repo.getName(), oaiIdCache);
        TimingLogger.reset();
        getRepositoryDAO().populatePreviousStatuses(repo.getName(), previousStatuses, false);
        TimingLogger.reset();
        cacheSetup = true;
    }

    private Long getRecordId(String oaiId) {
        String nonRedundantId = getUtil().getNonRedundantOaiId(oaiId);
		Long recId = oaiIdCache.getLong(nonRedundantId);
    	if (cacheSetup) {
    		return recId;
    	} else {
    		if (recId != null && recId != 0) return recId;
    		return getRepositoryDAO().getRecordId(repo.getName(), oaiId);
    	}
    }

    private void cacheRecordId(String oaiId, Long recordId) {
        String nonRedundantId = getUtil().getNonRedundantOaiId(oaiId);
		oaiIdCache.put(nonRedundantId, recordId);
    }
    
    private char getPreviousStatus(Long recordId) {
    	char prevStatus = (char) previousStatuses.get(recordId); 
        if (cacheSetup) {
        	return prevStatus;
        } else {
        	if (prevStatus != (char) 0) return prevStatus;
        	return getRepositoryDAO().getPreviousStatus(repo.getName(), recordId, false);
        }
    }
    
    private void cachePreviousStatus(Long recordId, byte status) {
    	previousStatuses.put(recordId, status);    	
    }
    
    @Override
    public void finishInner(boolean success) {
        super.finishInner(success);
        RecordCounts mostRecentIncomingRecordCounts =
                getRecordCountsDAO().getMostRecentIncomingRecordCounts(repo.getName());
        // I'm subtracting 1s from startTime because they might actually be equal by the second
        if (mostRecentIncomingRecordCounts == null) {
            LOG.error("*** HarvestManager.finishInner: mostRecentIncomingRecordCounts == null!");
            LogWriter.addInfo(harvestSchedule.getProvider().getLogFileName(), "Harvest Manager - unable to print record counts, null mostRecentIncomingRecordCounts!");
        } else if (mostRecentIncomingRecordCounts.getHarvestStartDate() == null) {
            LOG.error("*** HarvestManager.finishInner: mostRecentIncomingRecordCounts.getHarvestStartDate() == null!");
            LogWriter.addInfo(harvestSchedule.getProvider().getLogFileName(), "Harvest Manager - unable to print record counts, null harvest start date!");
        } else if (recordsProcessedThisRun > 0) {
            for (RecordCounts rc : new RecordCounts[] {
                    mostRecentIncomingRecordCounts,
                    getRecordCountsDAO().getTotalIncomingRecordCounts(repo.getName()) }) {
                LOG.debug("harvestSchedule: " + harvestSchedule);
                LOG.debug("harvestSchedule.getProvider(): " + harvestSchedule.getProvider());
                LOG.debug("harvestSchedule.getProvider().getLogFileName(): " + harvestSchedule.getProvider().getLogFileName());
                LOG.debug("rc: " + rc);
                LOG.debug("repo: " + repo);
                LogWriter.addInfo(harvestSchedule.getProvider().getLogFileName(), rc.toString(repo.getName()));
                // LogWriter.addInfo(harvestSchedule.getProvider().getLogFileName(), " %************************%");
            }
            LogWriter.addInfo(harvestSchedule.getProvider().getLogFileName(), repo.getRecordStatsByType());
            // LogWriter.addInfo(harvestSchedule.getProvider().getLogFileName(), " &&&&&&&&&&&&&&&&&&&&&&&&&&");

            // in case you find a reason to do record count calculations here, uncomment this, grab the desired type data from the map
            // below, and make your calculations.
            //
            // RecordCounts rc = getRecordCountsDAO().getTotalIncomingRecordCounts(repo.getName());
            // Map<String, AtomicInteger> counts4type = rc.getCounts().get(RecordCounts.TOTALS);
        } else {
            LOG.debug("HarvestManager will not write record counts to harvest log because recordsProcessThisRun=" + recordsProcessedThisRun);
        }
    }

    public void logError(Throwable t) {
        try {
            log.error(t.getMessage(), t);
            Provider provider = currentHarvest.getProvider();
            provider.setErrors(provider.getErrors() + 1);
            getProviderDAO().update(provider);
        } catch (DataException de) {
            throw new RuntimeException(de);
        }
        getUtil().throwIt(t);
    }

    public void validate(HarvestScheduleStep scheduleStep) throws DataException {
        Provider provider = harvestSchedule.getProvider();
        // Try to validate the repository. An exception will be thrown and caught if validation fails.
        // Validate that the repository conforms to the OAI protocol
        TimingLogger.log("about to validate repo");
        ValidateRepository validator = (ValidateRepository) MSTConfiguration.getInstance().getBean("ValidateRepository");

        validator.validate(harvestSchedule.getProvider().getId());

        TimingLogger.log("validated repo");
        deletedRecord = validator.getDeletedRecordSupport();

        // Get the provider from the repository so we know the formats and sets it
        // supports according to the validation we just performed
        harvestSchedule.setProvider(getProviderDAO().getById(harvestSchedule.getProvider().getId()));
        provider = harvestSchedule.getProvider();

        String metadataPrefix = scheduleStep.getFormat().getName();

        // Get the format we're to harvest
        Format format = getFormatDAO().getByName(metadataPrefix);

        // If the provider no longer supports the requested format we can't harvest it
        if (!harvestSchedule.getProvider().getFormats().contains(format)) {
            String errorMsg = "The harvest could not be run because the MetadataFormat " + metadataPrefix +
                    " is no longer supported by the OAI repository " + provider.getOaiProviderUrl() + ".";

            LogWriter.addError(harvestSchedule.getProvider().getLogFileName(), errorMsg);
            sendReportEmail(errorMsg);
            throw new RuntimeException(errorMsg);
        } // end if(format no longer supported)

        String setSpec = null;

        // If there was a set, set up the setSpec
        if (scheduleStep.getSet() != null)
            setSpec = scheduleStep.getSet().getSetSpec();

        // If the provider no longer contains the requested set we can't harvest it
        if (setSpec != null && !harvestSchedule.getProvider().getSets().contains(getSetDAO().getBySetSpec(setSpec))) {
            String errorMsg = "The harvest could not be run because the Set " + setSpec +
                    " is no longer supported by the OAI repository " + provider.getOaiProviderUrl() + ".";

            LogWriter.addError(harvestSchedule.getProvider().getLogFileName(), errorMsg);
            sendReportEmail(errorMsg);
            throw new RuntimeException(errorMsg);
        }
    }

    public boolean doSomeWork() {
        running.lock();
        boolean retVal = true;
        /*
         * BDA - I moved this to the bottom of this method
        String testHarvestMaxRequests = config.getProperty("test.harvest.maxRequests");
        recordsProcessed;
        if (testHarvestMaxRequests != null) {
            int maxRequests = Integer.parseInt(testHarvestMaxRequests);
            if (maxRequests > 0 && maxRequests == requestsSent4Step) {
                retVal = false;
            }
        }
        */
        requestsSent4Step++;
        log.debug("harvestScheduleSteps.size(): " + harvestScheduleSteps.size());
        if (retVal && harvestScheduleStepIndex >= 0 && harvestScheduleStepIndex < harvestScheduleSteps.size()) {
            Provider provider = null;
            try {
                HarvestScheduleStep scheduleStep = harvestScheduleSteps.get(harvestScheduleStepIndex);

                String metadataPrefix = null;
                if (scheduleStep != null && scheduleStep.getFormat() != null) {
                    metadataPrefix = scheduleStep.getFormat().getName();
                }

                String setSpec = null;

                // If there was a set, set up the setSpec
                if (scheduleStep.getSet() != null)
                    setSpec = scheduleStep.getSet().getSetSpec();

                HarvestSchedule schedule = scheduleStep.getSchedule();
                String baseURL = currentHarvest.getProvider().getOaiProviderUrl();

                LogWriter.addInfo(scheduleStep.getSchedule().getProvider().getLogFileName(), "Starting harvest of " + baseURL);

                provider = harvestSchedule.getProvider();
                String request = null;
                Document doc = null;
                if (baseURL.startsWith("file:")) {
                    File pwd = new File(".");
                    log.debug("pwd: " + pwd.getAbsolutePath());
                    pwd = new File(".");
                    log.debug("pwd: " + pwd.getAbsolutePath());
                    // pwd = new File(new URI("file://."));
                    // log.debug("pwd: "+pwd.getAbsolutePath());
                    String folderStr = baseURL.substring("file://".length());
                    log.debug("folderStr: " + folderStr);
                    File folder = new File(folderStr);
                    boolean nextOne = false;
                    File file2harvest = null;
                    log.debug("provider.getLastOaiRequest(): " + provider.getLastOaiRequest());
                    log.debug("provider: " + provider);
                    log.debug("provider.hashCode(): " + provider.hashCode());
                    if (!folder.exists()) {
                        throw new RuntimeException("folder " + folder.getAbsolutePath() + " does not exist");
                    }
                    if (folder.listFiles() != null) {
                        if (provider.getLastOaiRequest() != null) {
                            List<String> fileNames = new ArrayList<String>();
                            for (File file : folder.listFiles()) {
                                fileNames.add(file.getName());
                            }
                            if (!fileNames.contains(provider.getLastOaiRequest())) {
                                provider.setLastOaiRequest(null);
                            }
                        }

                        //TODO note during unit test, file_harvest, sometimes does not find file to harvest/hangs if a *~ file exists, why?
                        // maybe if running test 2x?
                        File[] files = folder.listFiles();
                        files = sortFiles(files);
                        for (File file : files) {
                            log.debug("file.getName(): " + file.getName());
                            log.debug("provider.getLastOaiRequest(): " + provider.getLastOaiRequest());
                            if (!file.getName().endsWith(".xml")) {
                                continue;
                            } else if (nextOne || provider.getLastOaiRequest() == null) {
                                file2harvest = file;
                                break;
                            } else {
                                if (provider.getLastOaiRequest().equals(file.getName())) {
                                    nextOne = true;
                                }
                            }
                        }
                    }
                    log.info("file2harvest: " + file2harvest);
                    if (file2harvest == null) {
                        return false;
                    }
                    provider.setLastOaiRequest(file2harvest.getName());
                    lastOaiRequest = file2harvest.getName();
                    doc = new XmlHelper().getJDomDocument(getUtil().slurp(file2harvest));
                } else if (baseURL.startsWith("http:")) {
                    String verb = "ListRecords";
                    request = baseURL;

                    String baseRequest = null;

                    // If this is the first request, setup a ListRecords request with the
                    // correct metadataPrefix. If we are supposed harvest a specific set
                    // or use a known from or until parameter, set them here as well.
                    // if (hssFirstTime) {
                    if (resumptionToken == null) {
                        this.recordsProcessedThisRun = 0;
                        this.records2ProcessThisRun = 0;
                        validate(scheduleStep);

                        request += "?verb=" + verb;
                        request += "&metadataPrefix=" + metadataPrefix;

                        if (setSpec != null && setSpec.length() > 0) {
                            // strip off the first part of the setSpec because it's the reponame
                            int idx0 = setSpec.indexOf(':');
                            request += "&set=" + URLEncoder.encode(setSpec.substring(idx0 + 1), "UTF-8");
                        }

                        baseRequest = request;

                        // both of these null checks are pointless at this time as I'll
                        // always be passing in a start and end date
                        if (currentHarvest.getStartTime() != null) {
                            if (Provider.DAY_GRANULARITY.equals(provider.getGranularity())) {
                                request += "&from=" + printDate(currentHarvest.getStartTime());
                            } else if (Provider.SECOND_GRANULARITY.equals(provider.getGranularity())) {
                                request += "&from=" + printDateTime(currentHarvest.getStartTime());
                            }
                        }
                        if (currentHarvest.getEndTime() != null) {
                            if (Provider.DAY_GRANULARITY.equals(provider.getGranularity())) {
                                request += "&until=" + printDate(currentHarvest.getEndTime());
                            } else if (Provider.SECOND_GRANULARITY.equals(provider.getGranularity())) {
                                request += "&until=" + printDateTime(currentHarvest.getEndTime());
                            }
                        }

                        harvestSchedule.setRequest(baseRequest);
                        harvestSchedule.setStatus(Status.RUNNING);
                        getHarvestScheduleDAO().update(harvestSchedule, false);
                        currentHarvest.setRequest(request);
                        getHarvestDAO().update(currentHarvest);
                    } else {
                        try {
                            resumptionToken = URLEncoder.encode(resumptionToken, "utf-8");
                        } catch (UnsupportedEncodingException uee) {
                            log.error("couldn't encode resumption token: " + resumptionToken);
                        }
                        request += "?verb=" + verb + "&resumptionToken=" + resumptionToken;
                    }

                    LogWriter.addInfo(schedule.getProvider().getLogFileName(), "The OAI request is " + request);

                    if (log.isDebugEnabled()) {
                        log.debug("Sending the OAI request: " + request);
                    }

                    // Perform the harvest
                    TimingLogger.start("sendRequest");
                    doc = getHttpService().sendRequest(request);
                    /*
                    log.debug("doc: ");
                    if (log.isDebugEnabled())
                        log.debug(new XmlHelper().getString(doc.getRootElement()));
                    */
                    TimingLogger.stop("sendRequest");

                    provider.setLastOaiRequest(request);
                }
                
                
                TimingLogger.start("parseRecords");
                resumptionToken = parseRecords(metadataPrefix, doc, baseURL);
                log.debug("resumptionToken: " + resumptionToken);
                TimingLogger.stop("parseRecords");

                getProviderDAO().update(provider, false);

                LogWriter.addInfo(scheduleStep.getSchedule().getProvider().getLogFileName(), "Finished harvesting " + baseURL);
                // + ", " + recordsProcessed + " new records were returned by the OAI provider.");

            } catch (DataException de) {
                logError(de);
                retVal = false;
            } catch (HttpException he) {
                logError(he);
            } catch (Throwable t) {
                logError(t);
            }
            hssFirstTime = false;
            retVal = true;
            if (resumptionToken == null) {
                try {
                    if (provider.getNumberOfRecordsToHarvest() == 0) {
                        provider.setLastHarvestEndTime(new Date());
                        getProviderDAO().update(provider, false);
                    }
                } catch (Throwable t) {
                    LOG.debug("", t);
                }

                hssFirstTime = true;
                harvestScheduleStepIndex++;
                if (harvestScheduleStepIndex >= harvestScheduleSteps.size()) {
                    retVal = false;
                }
            }
            if (requestsSent4Step % 10 == 0) {
                long num = (recordsProcessedThisRun / (requestsSent4Step / 10));
                LOG.debug("**** Reset with performance, requestsSent4Step=" + requestsSent4Step + " recordsProcessedThisRun=" + num);
                TimingLogger.reset(num);
            }
        } else {
            retVal = false;
            TimingLogger.reset();
        }
        if (harvestSchedule.getProvider().getNumberOfRecordsToHarvest() > 0 &&
                harvestSchedule.getProvider().getNumberOfRecordsToHarvest() <= this.recordsProcessedThisRun) {
            hssFirstTime = true;
            harvestScheduleStepIndex++;
            if (harvestScheduleStepIndex >= harvestScheduleSteps.size()) {
                retVal = false;
            }
        }
        repo.commitIfNecessary(false, recordsProcessedThisRun, this.incomingRecordCounts, null);
        running.unlock();
        return retVal;
    }

    // this is for debug use with a harvest from filesystem, files have format like:
    // 7_969999_970000_6679727.xml
    // 7_974999_975000_6679727.xml
    // must then sort by token after 2nd _
    // also note that the first file gets the special name, initial.xml
    //
    // assumptions: already know files != null
    private File[] sortFiles(File[] files) {
        if (files.length < 2) {
            return files;
        }
        String name = files[0].getName();
        if (!name.startsWith("initial")) {
            StringTokenizer st = new StringTokenizer(name, "_");
            if (st.countTokens() >= 3) {
                // have at least 2 underscores...can proceed with sorting by token after 2nd underscore.
                return reallySortFiles(files);
            } else {
                return files;
            }
        } else if (files.length > 1) {
            name = files[1].getName();
            StringTokenizer st = new StringTokenizer(name, "_");
            if (st.countTokens() >= 3) {
                // have at least 2 underscores...can proceed with sorting by token after 2nd underscore.
                return reallySortFiles(files);
            } else
                return files;
        }
        return files;
    }

    // this is for debug use with a harvest from filesystem, files have format like:
    // 7_969999_970000_6679727.xml
    // 7_974999_975000_6679727.xml
    // must then sort by token after 2nd _
    //
    // by earlier method determined we have files containing 2 '_' characters, sort these by int after 2nd underscore.
    private File[] reallySortFiles(File[] files) {
        TreeMap<Long, File> map = new TreeMap<Long, File>();
        for (File file : files) {
            if (file.getName().startsWith("initial")) { // want this one to be 1st.
                map.put(0l, file);
            } else {
                StringTokenizer st = new StringTokenizer(file.getName(), "_");
                try {
                    st.nextToken();
                    st.nextToken();
                } catch (NoSuchElementException e) {
                    LOG.error("HarvestManager, trying to harvest from file, unexpected exception handling file " + file.toString(), e);
                    return files;
                }
                try {
                    long tokL = Long.parseLong(st.nextToken());
                    map.put(tokL, file);
                } catch (NumberFormatException nfe) {
                    LOG.error("HarvestManager, trying to harvest from file, unexpected exception handling file " + file.toString(), nfe);
                    return files;
                }
            }
        }
        Collection<File> collection = map.values();
        return collection.toArray(new File[0]);
    }

    @SuppressWarnings("unchecked")
    protected String parseRecords(String prefix, Document doc, String baseURL) {

        String resumption = null;
        Element root = doc.getRootElement();

        // Check whether or not the response contained an error
        // If it did, throw an exception describing the error
        Element errorEl = root.getChild("error", root.getNamespace());
        if (errorEl != null) {
            String errorCode = errorEl.getAttributeValue("code");
            log.info("errorCode: " + errorCode + " " + errorEl.getText());
            return null;
            // throw new RuntimeException("errorCode: "+errorCode+" "+errorEl.getText());
        }

        Element listRecordsEl = null;
        // Get the verb (ListRecords) element. Try to get it as though it were the child of
        // the root element. If that doesn't work, assume that it is the root element itself
        try {
            listRecordsEl = root.getChild("ListRecords", root.getNamespace());
        } catch (Throwable e) {
            listRecordsEl = root;
        }
        
        // If the record contained a resumption token, store that resumption token
        Element resumptionEl = listRecordsEl.getChild("resumptionToken", root.getNamespace());

        if (resumptionEl != null) {
            resumption = resumptionEl.getText();
        }
        log.debug("resumption: " + resumption);
        if (!StringUtils.isEmpty(resumption)) {
            try {
                this.records2ProcessThisRun = Integer.parseInt(resumptionEl.getAttributeValue("completeListSize"));
            } catch (Throwable t) {
                this.records2ProcessThisRun = -1;
            }
            log.debug("The resumption string is " + resumption);
        } else {
            resumption = null;
        }
      
        // Is this a "large" update?
        // If so, we will cache OAI IDs and previous statuses; otherwise, we hit the DB each time
        if (resumption != null) {
        	if (this.records2ProcessThisRun >= largeHarvestThreshold) {
        		log.info("This is a large update; we will cache OAI IDs (" + this.records2ProcessThisRun + " >= " + largeHarvestThreshold + ").");
                oaiIdCache.ensureCapacity((int) this.records2ProcessThisRun);
                setupCache();
        	} else {
        		log.info("This is not a large update; we will not need to cache OAI IDs (" + this.records2ProcessThisRun + " < " + largeHarvestThreshold + ").");
        	}
        } else {
    		log.info("This is not a large update; we will not need to cache OAI IDs (no resumptionToken; assuming it's a \"small\" update\").");        	
        }
        

        // Try to get the element containing the first record. It should be a child of the
        // verb element.
        Element recordEl = null;
        try {
            recordEl = listRecordsEl.getChild("record", root.getNamespace());
        } catch (Throwable e) {
            // Check the response for the request URL
            Element requestUrlElement;
            try {
                requestUrlElement = listRecordsEl.getChild("requestURL", root.getNamespace());
            } catch (Exception e1) {
                LogWriter.addError(currentHarvest.getProvider().getLogFileName(), "The OAI provider returned an invalid response to the ListRecords request.");
                sendReportEmail("The OAI provider returned an invalid response to the ListRecords request.");
                throw new RuntimeException("The data provider returned an invalid response to the ListRecords request: " + e.getMessage()); // exc. e more interesting than e1?
            }

            // If the response contained the URL, report the error "no records found"
            if (requestUrlElement != null) {
                LogWriter.addInfo(currentHarvest.getProvider().getLogFileName(), "The OAI provider did not return any records");
                sendReportEmail("The OAI provider did not return any records");
                // Return null to show that there were no records returned
                return null;
            }

            // If we got here, the URL element wasn't found. In this
            // case report the error as "invalid OAI response"
            LogWriter.addError(currentHarvest.getProvider().getLogFileName(), "The OAI provider returned an invalid response to the ListRecords request.");
            sendReportEmail("The OAI provider returned an invalid response to the ListRecords request.");
            throw new RuntimeException("The data provider returned an invalid response to the ListRecords request: " + e.getMessage());
        }

        // Loop over all records in the OAI response
        List<Element> recordsEl = listRecordsEl.getChildren("record", root.getNamespace());
        log.debug("recordsEl.size(): " + recordsEl.size());

        for (Object recordElObj : recordsEl) {
            recordEl = (Element) recordElObj;

            try {
                HarvestScheduleStep scheduleStep = harvestScheduleSteps.get(harvestScheduleStepIndex);
                TimingLogger.start("getRecordService().parse(recordEl)");
                Record record = getRecordService().parse(recordEl, currentHarvest.getProvider());
                TimingLogger.stop("getRecordService().parse(recordEl)");
                record.setFormat(scheduleStep.getFormat());
                record.setHarvest(currentHarvest);
                record.setProvider(currentHarvest.getProvider());

                String oaiId = record.getHarvestedOaiIdentifier();
                Long recordId = getRecordId(oaiId);
                char prevStatus = 0;
                if (recordId == null || recordId == 0) {
                    getRepositoryDAO().injectId(record);
                } else {
                    record.setId(recordId);
                    prevStatus = getPreviousStatus(recordId);
                    log.debug("found prevStatus: " + prevStatus);
                    record.setPreviousStatus(prevStatus);
                }
                cachePreviousStatus(record.getId(), (byte) record.getStatus());
                cacheRecordId(oaiId, record.getId());

                repo.addRecord(record);
                if (record.getSets() != null && record.getSets().size() > 1) {
                    for (Set s : record.getSets()) {
                        if (s.getSetSpec().contains(":")) {
                            incomingRecordCounts.incr(s.getSetTypeShort(), record.getStatus(), prevStatus);
                        }
                    }
                } else {
                    incomingRecordCounts.incr(RecordCounts.OTHER, record.getStatus(), prevStatus);
                }
                incomingRecordCounts.incr(null, record.getStatus(), prevStatus);
            } catch (Exception e) {
                log.error("An error occurred in insertion ", e);
            }
            this.recordsProcessedThisRun++;
        }


        return resumption;
    }

    /**
     * Builds and sends an email report about the harvest to the schedule's notify email address.
     *
     * @param problem
     *            The problem which prevented the harvest from finishing, or null if the harvest was successful
     */
    protected boolean sendReportEmail(String problem) {
        if (harvestSchedule.getNotifyEmail() != null && mailer.isConfigured()) {
            // The email's subject
            InetAddress addr = null;
            try {
                addr = InetAddress.getLocalHost();
            } catch (UnknownHostException e) {
                log.error("Host name query failed.", e);
            }
            String subject = "Results of harvesting " + harvestSchedule.getProvider().getOaiProviderUrl() + " by MST Server on " + addr.getHostName();

            // The email's body
            StringBuilder body = new StringBuilder();

            // First report any problems which prevented the harvest from finishing
            if (problem != null)
                body.append("The harvest failed for the following reason: ").append(problem).append("\n\n");

            /*
            if(this.records2ProcessThisRun!=0) {
                body.append("Total number of records available for harvest =").append(totalRecords).append(" \n");
                body.append("Number of records harvested =").append(recordsProcessed).append(" \n");
            }
            */

            return mailer.sendEmail(harvestSchedule.getNotifyEmail(), subject, body.toString());
        } else {
            // note, after configuring email, seem to have to restart MST for it to work.
            log.debug("HarvestManager.sendReportEmail-mail is not configured right! sendto:" +
                    harvestSchedule.getNotifyEmail() + " isConfigured:" + mailer.isConfigured());
            return false;
        }
    }

    public long getRecords2ProcessThisRun() {
        return this.records2ProcessThisRun;
    }

    public long getRecordsProcessedThisRun() {
        return this.recordsProcessedThisRun;
    }
}
