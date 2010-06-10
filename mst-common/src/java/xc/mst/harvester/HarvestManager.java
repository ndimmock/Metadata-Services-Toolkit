/**
  * Copyright (c) 2009 University of Rochester
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the MIT/X11 license. The text of the
  * license can be found at http://www.opensource.org/licenses/mit-license.php and copy of the license can be found on the project
  * website http://www.extensiblecatalog.org/.
  *
  */
package xc.mst.harvester;

import java.io.InputStream;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SimpleTimeZone;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.log4j.Logger;
import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;
import org.jdom.Document;
import org.jdom.Element;

import xc.mst.bo.harvest.Harvest;
import xc.mst.bo.harvest.HarvestSchedule;
import xc.mst.bo.harvest.HarvestScheduleStep;
import xc.mst.bo.processing.Job;
import xc.mst.bo.processing.ProcessingDirective;
import xc.mst.bo.provider.Format;
import xc.mst.bo.provider.Provider;
import xc.mst.bo.provider.Set;
import xc.mst.bo.record.Record;
import xc.mst.constants.Constants;
import xc.mst.dao.DataException;
import xc.mst.dao.DatabaseConfigException;
import xc.mst.dao.record.XcIdentifierForFrbrElementDAO;
import xc.mst.email.Emailer;
import xc.mst.manager.BaseManager;
import xc.mst.manager.IndexException;
import xc.mst.manager.record.DBRecordService;
import xc.mst.manager.record.RecordService;
import xc.mst.utils.LogWriter;
import xc.mst.utils.MSTConfiguration;
import xc.mst.utils.TimingLogger;


public class HarvestManager extends BaseManager {
	/**
	 * Stores sets that we got from the database so we don't need to repeat queries
	 */
	private Map<String, Set> setsLoaded = new HashMap<String, Set>();
	
	/**
	 * The number of records inserted for the current harvest
	 */
	private int recordsFound = 0;

	/**
	 * The total amount of time we've spent waiting for the OAI Toolkit (used for debugging)
	 */
	private long oaiRepositoryTime = 0;

	/**
	 * The total amount of time we've spent parsing and inserting records from an OAI response
	 */
	private long insertRecordsTime = 0;

	/**
	 * Timestamp before an OAI request is placed.  This is used to calculate the amount of time spent waiting for a response from the server.
	 */
	private long startOaiRequest = 0;

	/**
	 * Timestamp after an OAI response is received.  This is used to calculate the amount of time spent waiting for a response from the server.
	 */
	private long finishOaiRequest = 0;

	/**
	 * Timestamp before processing of the OAI response is begun.  This is used to calculate the amount of time spent processing the response
	 */
    private long resTokStartTime = 0;

	/**
	 * Timestamp after processing of the OAI response is finished.  This is used to calculate the amount of time spent processing the response
	 */
    private long resTokEndTime = 0;

	/**
	 * The granularity of the OAI repository we're harvesting (either GRAN_DAY or GRAN_SECOND)
	 */
	private int granularity = -1;

	/**
	 * The policy for tracking deleted records that the OAI repository uses (either DELETED_RECORD_NO, DELETED_RECORD_TRANSIENT, or DELETED_RECORD_PERSISTENT)
	 */
	private int deletedRecord = -1;

	/**
	 * True if this Harvester has received the kill command
	 */
	private boolean killed = false;

	/**
	 * True if this Harvester has received the pause command
	 */
	private boolean isPaused = false;

	/**
     * The harvest schedule being run
     */
	private HarvestSchedule schedule = null;

	/**
     * The harvest being run
     */
	private Harvest currentHarvest = null;

	/**
	 * Reference to the current running harvester
	 */
	private static Harvester runningHarvester;

	/**
	 * The provider being harvested
	 */
	private Provider provider = null;

	/**
	 * The ID of the Format entry with the metadataPrefix we're harvesting
	 */
	private Format format = null;

	/**
	 * True once we've logged an Exception
	 */
	private boolean loggedException = false;

	/**
	 * The number of records that could not be inserted correctly
	 */
	private int failedInserts = 0;

	/**
	 * The processing directives for the repository we're harvesting
	 */
	private List<ProcessingDirective> processingDirectives = null;

	/**
	 * A list of services to run after this service's processing completes
	 * The keys are the service IDs and the values are the IDs of the sets
	 * that service's records should get added to
	 */
	private HashMap<Integer, Integer> servicesToRun = new HashMap<Integer, Integer>();

	/**
	 * Used to send email reports
	 */
	private Emailer mailer = new Emailer();

	/**
	 * A list of warnings that occurred during the harvest
	 */
	private StringBuilder warnings = new StringBuilder();

	/**
	 * A list of errors that occurred during the harvest
	 */
	private StringBuilder errors = new StringBuilder();

	/**
	 * A reference to the logger which writes to the HarvestIn log file
	 */
	private static Logger log = Logger.getLogger("harvestIn");

	/**
	 * The number of records added by the current harvest
	 */
	private int addedCount = 0;

	/**
	 * The number of records updated by the current harvest
	 */
	private int updatedCount = 0;

	/**
	 * The number of warnings in the current harvest
	 */
	private int warningCount = 0;

	/**
	 * The number of errors in the current harvest
	 */
	private int errorCount = 0;

	/**
	 * HttpClient used for making OAI requests
	 */
	private HttpClient client = null;

	/**
	 * True iff there have not yet been any records harvested from the provider we're harvesting
	 */
	private boolean firstHarvest = false;

	/**
	 * Count of the records processed yet
	 */
	private static int processedRecordCount = 0;
	
	/**
	 * Count of the total records processed
	 */
	private static int totalRecordCount = 0;
	
	
	long totalPartTime = 0;
	long startPartTime = 0;
	long endPartTime = 0;
	
	long harvestStartTime = 0;
	long startTime = 0;
	long endTime = 0;
	long timeDiff = 0;

	
	/**
	 * Creates a Harvester that runs a given harvest schedule step and records the results
	 * using the passed <code>Harvest</code> Object.
	 *
	 * @param scheduleStep The <code>HarvestScheduleStep</code> with information on the harvest we're running
	 * @param currentHarvest The <code>Harvest</code> Object that will store the results of this harvest, such
	 *                       as the start and end times.  This should represent a Harvest that has already been
	 *                       written to the database.  The corrosponding harvest row will be updated with the
	 *                       results after the harvest finishes running.
	 * @throws DatabaseConfigException
	 */
	public void setup(HarvestScheduleStep scheduleStep, Harvest currentHarvest) throws DatabaseConfigException {
		this.currentHarvest = currentHarvest;
		this.schedule = scheduleStep.getSchedule();
		this.provider = schedule.getProvider();

		// Get the ProcessingDirectives which could match records harvested from the provider we're harvesting
		processingDirectives = getProcessingDirectiveDAO().getBySourceProviderId(provider.getId());
	} // end constructor Harvester(int, HarvestScheduleStep, Harvest)

	public void kill() {
		log.debug("HarvestManager.kill() called");
		killed = true;
	}

	public void harvest(String baseURL, String metadataPrefix, String setSpec,
            HarvestScheduleStep scheduleStep, Harvest currentHarvest) throws DatabaseConfigException {

		LogWriter.addInfo(scheduleStep.getSchedule().getProvider().getLogFileName(), "Starting harvest of " + baseURL);

		try {
			// Create a Harvester and use it to run the harvest
			setup(scheduleStep, currentHarvest);

			// Update the status of the harvest schedule
			//runningHarvester.persistStatus(Constants.STATUS_SERVICE_RUNNING);
			
			//BEGINNING_OF_ORIG_doHarvest_METHOD
			
			String errorMsg = null;
			String request = null;
	
			try {
				firstHarvest = recordService.getCountByProviderId(schedule.getProvider().getId()) == 0;
				TimingLogger.log("firstHarvest: "+firstHarvest);
			} catch (IndexException e2) {
				log.error("An IndexExeption occurred while harvesting " , e2);
	
				errorMsg = "Harvest failed because the Solr index could not be accessed.  Check your configuration.";
	
				LogWriter.addError(schedule.getProvider().getLogFileName(), errorMsg);
				errorCount++;
	
				sendReportEmail(errorMsg);
				persistStatus(Constants.STATUS_SERVICE_ERROR);
				throw new RuntimeException(errorMsg);
			}

			// Try to validate the repository.  An exception will be thrown and caught if validation fails.
			try {
				// Validate that the repository conforms to the OAI protocol
				TimingLogger.log("about to validate repo");
				ValidateRepository validator = (ValidateRepository)MSTConfiguration.getInstance().getBean("ValidateRepository");
				
				validator.validate(schedule.getProvider().getId());
	
				TimingLogger.log("validated repo");
				
				granularity = validator.getGranularity();
				deletedRecord = validator.getDeletedRecordSupport();
	
				// Get the provider from the repository so we know the formats and sets it
				// supports according to the validation we just performed
				schedule.setProvider(getProviderDAO().getById(schedule.getProvider().getId()));
				provider = schedule.getProvider();
	
				// Get the format we're to harvest
				format = getFormatDAO().getByName(metadataPrefix);
	
				// If the provider no longer supports the requested format we can't harvest it
				if(!schedule.getProvider().getFormats().contains(format)) {
					errorMsg = "The harvest could not be run because the MetadataFormat " + metadataPrefix + " is no longer supported by the OAI repository " + baseURL + ".";
	
					LogWriter.addError(schedule.getProvider().getLogFileName(), errorMsg);
					errorCount++;
	
					loggedException = true;
	
					sendReportEmail(errorMsg);
	
					throw new RuntimeException(errorMsg);
				} // end if(format no longer supported)
	
				// If the provider no longer contains the requested set we can't harvest it
				if(setSpec != null && !schedule.getProvider().getSets().contains(getSetDAO().getBySetSpec(setSpec))) {
					errorMsg = "The harvest could not be run because the Set " + setSpec + " is no longer supported by the OAI repository " + baseURL + ".";
	
					LogWriter.addError(schedule.getProvider().getLogFileName(), errorMsg);
					errorCount++;
	
					loggedException = true;
	
					sendReportEmail(errorMsg);
	
					throw new RuntimeException(errorMsg);
				} // end if(set no longer supported)
			} catch (Throwable e) {
				log.error(e.getMessage());
	
				LogWriter.addError(schedule.getProvider().getLogFileName(), e.getMessage());
				errorCount++;
	
				loggedException = true;
	
				sendReportEmail(e.getMessage());
				persistStatus(Constants.STATUS_SERVICE_ERROR);
				throw e;
			}
	
			try {
				killed = false;
	
				String verb = "ListRecords";
				String resumption = null;
	
				// If metadataPrefix is not specified, log an error and abort
				if (metadataPrefix == null) {
					errorMsg = "The harvest could not be run because the MetadataFormat to harvest was not specified.";
					log.error(errorMsg);
	
					LogWriter.addError(schedule.getProvider().getLogFileName(), errorMsg);
					errorCount++;
	
					loggedException = true;
	
					sendReportEmail(errorMsg);
	
					throw new RuntimeException(errorMsg);
				}
				
				Date startDate = new Date();
				startTime = new Date().getTime();
				harvestStartTime = startTime;
	
				// This loop places a ListRecords request, and then continues placing requests
				// until it receives a null resumption token
				do {
					// Abort the harvest if the harvester was killed
					checkSignal(baseURL);
	
					request = baseURL;
	
					// If this is the first request, setup a ListRecords request with the
					// correct metadataPrefix.  If we are supposed harvest a specific set
					// or use a known from or until parameter, set them here as well.
					if (resumption == null) {
						request += "?verb=" + verb;
						request += "&metadataPrefix=" + metadataPrefix;
	
						if (setSpec != null && setSpec.length() > 0)
							request += "&set=" + setSpec;
	
						//TODO check harvest schedule
						Date from = scheduleStep.getLastRan();
						if (from != null) {
							request += "&from=" + formatDate(granularity, from);						
						}
	
						request += "&until=" + formatDate(granularity, startDate);
					} else {
						// Try to encode the resumption token to include it in the URL.
						// Don't worry if encoding it failed because the OAI request may work anyway
						try {
							resumption = URLEncoder.encode(resumption, "utf-8");
						} catch (Exception e) {
							log.warn("An error occurred when encoding the resumption token for use in a URL.", e);
	
							LogWriter.addWarning(schedule.getProvider().getLogFileName(), "An error occurred when trying to encode the resumption token returned by the provider as UTF-8.  The resumption token was: " + resumption);
							warningCount++;
						}
	
						request += "?verb=" + verb + "&resumptionToken=" + resumption;
					}
					
					LogWriter.addInfo(schedule.getProvider().getLogFileName(), "The OAI request is " + request);
					
					currentHarvest.setRequest(request);
					getHarvestDAO().update(currentHarvest);
	
					if (log.isDebugEnabled()) {
						log.debug("Sending the OAI request: " + request);
					}
	
					// Perform the harvest
					TimingLogger.start("sendRequest");
				    Document doc = getHttpService().sendRequest(request);
				    TimingLogger.stop("sendRequest");
	
				    TimingLogger.start("extractRecords");
	                resumption = parseRecords(metadataPrefix, doc, baseURL);
	                TimingLogger.stop("extractRecords");
	                
	                if (MSTConfiguration.getInstance().isPerformanceTestingMode() &&
	                		processedRecordCount > 2000000) {
	                	resumption = null; // I only want to run the 5,000 for now
	                }
	    			if (recordService instanceof DBRecordService) {
	    				((DBRecordService)recordService).commit(0, false);
	    			} else if (processedRecordCount % 50000 == 0) {
	    				TimingLogger.reset(false);
	    				TimingLogger.stop("sinceLastCommit");
	    				TimingLogger.start("sinceLastCommit");
	    			}
				} while(resumption != null); // Repeat as long as we get a resumption token
				if (recordService instanceof DBRecordService) {
					((DBRecordService)recordService).commit(0, true);
				}
			} // end try(run the harvest)
			catch (Hexception he)
			{
				if(!killed)
					persistStatus(Constants.STATUS_SERVICE_ERROR);
				
				log.error("A Hexeption occurred while harvesting " + baseURL, he);
	
				// Log the error for the user and send them a report email if we didn't already
				if(!loggedException)
				{
					LogWriter.addError(schedule.getProvider().getLogFileName(), "An internal error occurred while executing the harvest: " + he.getMessage());
					errorCount++;
	
					sendReportEmail("An internal error occurred while executing the harvest.");
				} // end if(we didn't already log the error)
	
	
				// Throw the Exception so the calling code knows something went wrong
				throw new Hexception(he.getMessage() + "\n, request was " + request);
			} // end catch(Hexception)
			catch (OAIErrorException oaie)
			{
				if(oaie.getOAIErrorCode().contains("noRecordsMatch"))
					return;
	
				log.error("An OAIErrorExeption occurred while harvesting " + baseURL, oaie);
	
				if(!killed)
					persistStatus(Constants.STATUS_SERVICE_ERROR);
	
				// Log the error for the user and send them a report email
				LogWriter.addError(schedule.getProvider().getLogFileName(), "The OAI provider returned the following error: " + oaie.getOAIErrorCode() + "," + oaie.getOAIErrorMessage());
				errorCount++;
	
				sendReportEmail("The OAI provider returned the following error: " + oaie.getOAIErrorCode() + ", " + oaie.getOAIErrorMessage());
	
				// Throw the Exception so the calling code knows something went wrong
				throw new OAIErrorException(oaie.getOAIErrorCode(), oaie.getOAIErrorMessage());
			} // end catch(OAIErrorException)
			catch (Throwable e)
			{
				if(!killed)
					persistStatus(Constants.STATUS_SERVICE_ERROR);
	
				log.error("An error occurred while harvesting " + baseURL, e);
	
				LogWriter.addError(schedule.getProvider().getLogFileName(), "An internal error occurred while executing the harvest: " + e.getMessage());
				errorCount++;
	
	
				// Throw the error so the calling code knows something went wrong
				throw new Hexception("Internal harvester error: " + e);
	
			} // end catch(Throwable)
			finally
			{
				
				if(killed)
					persistStatus(Constants.STATUS_SERVICE_CANCELED);
				
				// Process the records we harvested
				try
				{
					// Write the next XC ID for raw Records
					getXcIdentifierForFrbrElementDAO().writeNextXcId(XcIdentifierForFrbrElementDAO.ELEMENT_ID_RECORD);
	
					// Reopen the reader so it can see the record inputs we inserted for this harvest
					TimingLogger.log("before commit to solr");
					getSolrIndexManager().commitIndex();
					TimingLogger.log("committed to solr");
					
					endTime = new Date().getTime();
	    			timeDiff = endTime - startTime;
	            	LogWriter.addInfo(schedule.getProvider().getLogFileName(), "Indexed " + recordsFound + " records so far. Finished commiting to index. Time taken = " + (timeDiff / (1000*60*60)) + "hrs  " + ((timeDiff % (1000*60*60)) / (1000*60)) + "mins  " + (((timeDiff % (1000*60*60)) % (1000*60)) / 1000) + "sec  " + (((timeDiff % (1000*60*60)) % (1000*60)) % 1000) + "ms  ");
	            	timeDiff = endTime - harvestStartTime;
	            	LogWriter.addInfo(schedule.getProvider().getLogFileName(), "Total time taken for harvest = " + (timeDiff / (1000*60*60)) + "hrs  " + ((timeDiff % (1000*60*60)) / (1000*60)) + "mins  " + (((timeDiff % (1000*60*60)) % (1000*60)) / 1000) + "sec  " + (((timeDiff % (1000*60*60)) % (1000*60)) % 1000) + "ms  ");
	
	    			
	
				} // end try(schedule the services that the records triggered)
				catch(IndexException e)
				{
					persistStatus(Constants.STATUS_SERVICE_ERROR);
					
					log.error("An error occurred while managing the Index", e);
	
					LogWriter.addError(schedule.getProvider().getLogFileName(), "An internal error occurred while managing the Index.");
					errorCount++;
				} // end catch(IOException)
			} // end finally(reopen the index and schedule the triggered services)
			
			//END_OF_ORIG_doHarvest_METHOD
			
			log.info("Records harvested " + runningHarvester.recordsFound + ", failed inserts " + runningHarvester.failedInserts);
	
	
			LogWriter.addInfo(scheduleStep.getSchedule().getProvider().getLogFileName(), "Finished harvesting " + baseURL + ", " + runningHarvester.recordsFound + " new records were returned by the OAI provider.");
	
			// Report the number of records which could not be added to the index due to an error
			if(runningHarvester.failedInserts > 0)
			{
				LogWriter.addWarning(scheduleStep.getSchedule().getProvider().getLogFileName(), runningHarvester.failedInserts + " records were not able to be added or updated in the index.");
				runningHarvester.warningCount++;
			}
	
			// Send an Email report on the results of the harvest TODO
			runningHarvester.sendReportEmail(null);
		}
		catch (Hexception e) {
	
			throw e;
		}
		catch (OAIErrorException e) {
			throw e;
			
		}
		catch(DatabaseConfigException e)
		{
			log.error("Unable to connect to the database with the parameters defined in the configuration file.", e);
		}
		finally // Update the error and warning count for the provider
		{
			try
			{
				// Load the provider again in case it was updated during the harvest
				Provider provider = runningHarvester.getProviderDAO().getById(scheduleStep.getSchedule().getProvider().getId());
	
				// Increase the warning and error counts as appropriate, then update the provider
				provider.setWarnings(provider.getWarnings() + runningHarvester.warningCount);
				provider.setErrors(provider.getErrors() + runningHarvester.errorCount);
				provider.setRecordsAdded(provider.getRecordsAdded() + runningHarvester.addedCount);
				provider.setRecordsReplaced(provider.getRecordsReplaced() + runningHarvester.updatedCount);
	
				runningHarvester.getProviderDAO().update(provider);
			}
			catch (DataException e)
			{
				log.warn("Unable to update the provider's warning and error counts due to a Data Exception.", e);
			}
		}
	}

	protected String parseRecords(String prefix, Document doc, String baseURL) {
		
		String resumption = null;
		Element root = doc.getRootElement();

		// Check whether or not the response contained an error
		// If it did, throw an exception describing the error
		Element errorEl = root.getChild("error");
		if (errorEl != null) {
			String errorCode = errorEl.getAttributeValue("code");
			throw new RuntimeException("errorCode: "+errorCode+" "+errorEl.getText());
		}

		Element listRecordsEl = null;
		// Get the verb (ListRecords) element.  Try to get it as though it were the child of
		// the root element.  If that doesn't work, assume that it is the root element itself
		try {
			listRecordsEl = root.getChild("ListRecords");
		} catch (Throwable e){
			listRecordsEl = root;
		}

		// Try to get the element containing the first record.  It should be a child of the
		// verb element.
		Element recordEl = null;
		try
		{
			recordEl = listRecordsEl.getChild("record");
		} catch (Throwable e) {
			// Check the response for the request URL
			Element requestUrlElement = listRecordsEl.getChild("requestURL");

			// If the response contained the URL, report the error "no records found"
			if (requestUrlElement != null) {
				LogWriter.addInfo(schedule.getProvider().getLogFileName(), "The OAI provider did not return any records");
				loggedException = true;
				sendReportEmail("The OAI provider did not return any records");
				// Return null to show that there were no records returned
				return null;
			}

			// If we got here, the URL element wasn't found.  In this
			// case report the error as "invalid OAI response"
			LogWriter.addError(schedule.getProvider().getLogFileName(), "The OAI provider retured an invalid response to the ListRecords request.");
			errorCount++;
			loggedException = true;
			sendReportEmail("The OAI provider retured an invalid response to the ListRecords request.");
			throw new RuntimeException("The data provider returned an invalid response to the ListRecords request: " + e.getMessage());
		}

		resTokStartTime = System.currentTimeMillis();
		log.info("Time taken between placing the request and begining to process the reply " + (resTokStartTime-finishOaiRequest));

		// Loop over all records in the OAI response
		while (recordEl != null) {
			TimingLogger.start("parseRecords loop");
			TimingLogger.start("erl - 1");
			// Check to see if the service was paused or canceled
			checkSignal(baseURL);

            // If the schedule is null set the provider_id to 1,
            // otherwise set it to the provider_id associated with
            // the provider.
            int providerId = (schedule == null ? 1 : schedule.getProvider().getId());
            String oaiIdentifier="";
            Date oaiDatestamp = null;
            
            Element oaiXmlEl = null;

            // Get the identifier and the datestamp from the header
            Element headerEl = recordEl.getChild("header");
			Element identifierElement = headerEl.getChild("identifier");
			Element datestampElement = headerEl.getChild("datestamp");
			oaiIdentifier = identifierElement.getText();

			String oaiDateString = datestampElement.getText();
			try {
				if (oaiDateString != null && oaiDateString.length() > 0) {
					oaiDateString = oaiDateString.replace('T', ' ');
					oaiDateString = oaiDateString.replaceFirst("Z", "");
					oaiDateString = oaiDateString.replaceFirst("z", "");
					
					TimingLogger.start("sdf");
					oaiDatestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(oaiDateString);
					TimingLogger.stop("sdf");
				}
			} catch(ParseException pe) {
				log.error("Parsing exception occured while converting String " + oaiDateString + " to date" );
			}

			// Try to insert the record
			try {
				// Get the XML from the record
				Element metadataEl = recordEl.getChild("metadata");

				// If we found the metadata Node, get the OAI XML from it
				if (metadataEl != null) {
					if (metadataEl.getChildren() != null) {
						oaiXmlEl = (Element)metadataEl.getChildren().get(0);
					} else {
						throw new RuntimeException("metadata has no children");
					}	
				}

				// Get the status of the record
				String status = headerEl.getAttributeValue("status");

				// Check whether or not the record is deleted
				boolean deleted = false;
				if (status != null && status.equalsIgnoreCase("deleted"))
					deleted = true;

				Record record = new Record();
				record.setProvider(provider);
				record.setFormat(format);
				record.setOaiIdentifier(oaiIdentifier);
				record.setOaiDatestamp(oaiDatestamp);
				record.setOaiXmlEl(oaiXmlEl);
				record.setOaiHeaderEl(headerEl);
				record.setDeleted(deleted);
				
				// Get the sets in which the record appears
				Element setSpecElement = findSibling(datestampElement, "setSpec");
				
				TimingLogger.stop("erl - 1");
				TimingLogger.start("erl - 2");
				// Loop over the record's sets and add each to the record BO
				while (setSpecElement != null)
				{

					// Check to see if the service was paused or canceled
					checkSignal(baseURL);

					String setSpec = provider.getName().replace(' ', '-') + ":" + getContent(setSpecElement);

					// Split the set into its components
					String[] setSpecLevels = setSpec.split(":");

					// This will build the setSpecs to which the record belongs
					StringBuilder setSpecAtLevel = new StringBuilder();

					// Loop over all levels in the set spec
					for(String setSpecLevel : setSpecLevels)
					{
						// Append the set at the current level to the setSpec at the previous level to
						// get the setSpec for the current level. Append colons as needed
						setSpecAtLevel.append(setSpecAtLevel.length() <= 0 ? setSpecLevel : ":" + setSpecLevel);

						String currentSetSpec = setSpecAtLevel.toString();
						
						// If the set's already in the index, get it
						Set set = null;
						
						if(setsLoaded.containsKey(currentSetSpec))
							set = setsLoaded.get(currentSetSpec);
						else
						{
							set = getSetDAO().getBySetSpec(currentSetSpec);
	
							// Add the set if there wasn't already one in the database
							if(set == null)
							{
								set = new Set();
								set.setSetSpec(currentSetSpec);
								set.setDisplayName(currentSetSpec);
								set.setIsProviderSet(false);
								set.setIsRecordSet(true);
								TimingLogger.start("setDao.insertForProvider");
								getSetDAO().insertForProvider(set, providerId);
								TimingLogger.stop("setDao.insertForProvider");
							} // end if(the set wasn't found)
							
							setsLoaded.put(currentSetSpec, set);
						}

						// Add the set's ID to the list of sets to which the record belongs
						record.addSet(set);
					} // end loop over the set levels

					// Get the next setSpec from the OAI response
					setSpecElement = findSibling(setSpecElement, "setSpec");
				} // end loop over setSpecs for the record

				// Set the harvest which harvested the record
				record.setHarvest(currentHarvest);
				
				TimingLogger.stop("erl - 2");
				TimingLogger.start("erl - 3");
				// If the provider has been harvested before, check whether or not this
				// record already exists in the database
				Record oldRecord = (firstHarvest ? null : recordService.getByOaiIdentifierAndProvider(oaiIdentifier, providerId));

				// If the current record is a new record, insert it
				TimingLogger.start("insert record");
				if(oldRecord == null) {
					insertNewRecord(record);
				// Otherwise we've seen the record before.  Update or delete it as appropriate
				}
				else
				{
					if(deleted) {
						deleteExistingRecord(oldRecord);
					} else {
						updateExistingRecord(record, oldRecord);
					}
				} // end else(the record already existed it the index)
				TimingLogger.stop("erl - 3");
				TimingLogger.stop("insert record");
				processedRecordCount++;
			} // end try(insert the record)
			catch(Hexception hex){
				log.error("Hexception occured.", hex);
				throw hex;
			}
			catch (Exception e)
			{
				failedInserts++;
				log.error("An error occurred in insertion ", e);
			} // end catch(Exception)

			recordEl = findSibling(recordEl, "record", "resumptionToken");

            recordsFound++;
            if(recordsFound % 100000 == 0) {
            	
            	endTime = new Date().getTime();
    			timeDiff = endTime - startTime;
    		
            	LogWriter.addInfo(schedule.getProvider().getLogFileName(), "Indexed " + recordsFound + " records so far. Time taken for this 100k records = " + (timeDiff / (1000*60*60)) + "hrs  " + ((timeDiff % (1000*60*60)) / (1000*60)) + "mins  " + (((timeDiff % (1000*60*60)) % (1000*60)) / 1000) + "sec  " + (((timeDiff % (1000*60*60)) % (1000*60)) % 1000) + "ms  ");
            	startTime = new Date().getTime();
            }

            // If the record contained a resumption token, store that resumption token
			if (recordEl != null && recordEl.getNodeName().equals("resumptionToken"))
			{
				resumption = getContent(recordEl);
				totalRecordCount = Integer.parseInt(recordEl.getAttribute("completeListSize"));
				
				log.debug("The resumption string is " + resumption);
				
				
				if (resumption.length() == 0)
					resumption = null;

				break;
			} // end if(record contained a resumptionToken)
			TimingLogger.stop("extractRecords loop");
			
		} // end loop over record elements

		resTokEndTime = System.currentTimeMillis();

		log.info("Time to clear the resumption token " + (resTokEndTime - resTokStartTime));
		insertRecordsTime += (resTokEndTime - resTokStartTime);
		log.info("Total time to clear resumption tokens " + insertRecordsTime);
		return resumption;
	} // end method extractRecords(String, Document, String)

	/**
	 * Inserts a record in the Lucene index and sets up RecordInput values
	 * for any processing directives the record matched so the appropriate
	 * services process the record
	 *
	 * @param record The record to insert
	 */
	private void insertNewRecord(Record record)
	{
		try
		{
			// Run the processing directives against the record we're inserting
			TimingLogger.start("checkProcessingDirectives");
			checkProcessingDirectives(record);
			TimingLogger.stop("checkProcessingDirectives");
			
			if(!recordService.insert(record))
				log.error("Failed to insert the new record with the OAI Identifier " + record.getOaiIdentifier() + ".");
			else
				addedCount++;
		} // end try(insert the record)
		catch (DataException e)
		{
			failedInserts++;
			log.error("An exception occurred while inserting the record into the Lucene index.", e);
		} // end catch(DataException)
		catch (IndexException ie)
		{
			failedInserts++;
			log.error("An exception occurred while inserting the record into the Lucene index.", ie);
		}
	} // end method insertNewRecord(Record)

	/**
	 * Updates a record in the Lucene index and sets up RecordInput values
	 * for any processing directives the record matched so the appropriate
	 * services reprocess the record after the update
	 *
	 * @param newRecord The record as it should look after the update (the record ID is not set)
	 * @param oldRecord The record in the Lucene index which needs to be updated
	 */
	private void updateExistingRecord(Record newRecord, Record oldRecord)
	{
		try
		{
			// Set the new record's ID to the old record's ID so when we call update()
			// on the new record it will update the correct record in the Lucene index
			newRecord.setId(oldRecord.getId());
			newRecord.setCreatedAt(oldRecord.getCreatedAt());
			newRecord.setUpdatedAt(new Date());

			// Run the processing directives against the record we're updating
			checkProcessingDirectives(newRecord);

			if(!recordService.update(newRecord))
				log.error("The update failed for the record with ID " + newRecord.getId() + ".");
			else
				updatedCount++;
		} // end try(update the record)
		catch (DataException e)
		{
			failedInserts++;
			log.error("An exception occurred while updating the record into the Lucene index.", e);
		} // end catch(DataException)
		catch (IndexException ie)
		{
			failedInserts++;
			log.error("An exception occurred while updating the record into the Lucene index.", ie);
		}
	} // end method updateExistingRecord(Record, Record)

	/**
	 * Deletes a record in the Lucene index and sets up RecordInput values
	 * for any processing directives the record matched so the appropriate
	 * services reprocess the record after the delete
	 *
	 * @param deleteMe The record to delete
	 */
	private void deleteExistingRecord(Record deleteMe)
	{
		try
		{
			deleteMe.setDeleted(true);
			deleteMe.setUpdatedAt(new Date());

			// Run the processing directives against the record we're deleting
			checkProcessingDirectives(deleteMe);

			if(!recordService.update(deleteMe))
				log.error("Failed to delete the new record with the ID " + deleteMe.getId() + ".");
			else
				updatedCount++;
		} // end try(delete the record)
		catch (DataException e)
		{
			failedInserts++;
			log.error("An exception occurred while deleting the record from the Lucene index.", e);
		} // end catch(DataException)
		catch (IndexException ie)
		{
			failedInserts++;
			log.error("An exception occurred while deleting the record into the Lucene index.", ie);
		}
	} // end method deleteExistingRecord(Record)

	/**
	 * Runs the processing directives for this harvest against the record.  For all matching
	 * processing directives, adds the appropriate recordInput objects to the Lucene index.
	 * Also adds the service ID for all matched processing directives to the list of services
	 * to run when the harvest completes.
	 *
	 * @param record The record to match against the processing directives
	 */
	private void checkProcessingDirectives(Record record)
	{
		// Maintain a list of processing directives which were matched
		ArrayList<ProcessingDirective> matchedProcessingDirectives = new ArrayList<ProcessingDirective>();

		boolean matchedFormat = false;
		boolean matchedSet = false;
		
		// Loop over the processing directives and check if any of them match the record
		for(ProcessingDirective processingDirective : processingDirectives)
		{
			matchedFormat = false;
			matchedSet = false;

			// Check if the record matches any of the metadata formats for the current processing directive
			if(processingDirective.getTriggeringFormats().contains(record.getFormat())) {
				matchedFormat = true;
			}

			// check if the record is in any of the sets for the current processing directive
			if(processingDirective.getTriggeringSets() != null && processingDirective.getTriggeringSets().size() > 0) 
			{
				for(Set set : record.getSets())
				{
					if(processingDirective.getTriggeringSets().contains(set))
					{
						matchedSet = true;
						break;
					} 
				} 
			} else {
				matchedSet = true;
			}
			
			if (matchedFormat && matchedSet) {
				matchedProcessingDirectives.add(processingDirective);
			}
		} // end loop over processing directives

		// Loop over the matched processing directives.  Add the appropriate record inputs and add the
		// correct services to the list of services to run after the harvest completes
		for(ProcessingDirective matchedProcessingDirective : matchedProcessingDirectives)
		{
			record.addInputForService(matchedProcessingDirective.getService());
			record.removeProcessedByService(matchedProcessingDirective.getService());

			Integer serviceId = new Integer(matchedProcessingDirective.getService().getId());

			if(!servicesToRun.containsKey(serviceId)) {
				int outputSetId = new Integer(matchedProcessingDirective.getOutputSet() == null ? 0 : matchedProcessingDirective.getOutputSet().getId());
				servicesToRun.put(serviceId, outputSetId);
				// Add jobs to database
				try {
					Job job = new Job(matchedProcessingDirective.getService(), outputSetId, Constants.THREAD_SERVICE);
					job.setOrder(jobService.getMaxOrder() + 1); 
					jobService.insertJob(job);
				} catch (DatabaseConfigException dce) {
					log.error("DatabaseConfig exception occured when ading jobs to database", dce);
				}
			}
		} // end loop over matched processing directives
	} // end method checkProcessingDirectives(Record)

	/**
	 * Builds and sends an email report about the harvest to the schedule's notify email address.
	 *
	 * @param problem The problem which prevented the harvest from finishing, or null if the harvest was successful
	 */
	private boolean sendReportEmail(String problem)
	{
		if (schedule.getNotifyEmail() != null && mailer.isConfigured()) {
			// The email's subject
			InetAddress addr = null;
			try {
				addr = InetAddress.getLocalHost();
			} catch (UnknownHostException e) {
				log.error("Host name query failed.", e);
			}
			String subject = "Results of harvesting " + schedule.getProvider().getOaiProviderUrl() +" by MST Server on " + addr.getHostName();
	
			// The email's body
			StringBuilder body = new StringBuilder();
	
			// First report any problems which prevented the harvest from finishing
			if(problem != null)
				body.append("The harvest failed for the following reason: ").append(problem).append("\n\n");
	
			// Report on the number of records inserted successfully and the number of failed inserts
			if((processedRecordCount!=totalRecordCount) && totalRecordCount!=0)
				body.append("Error: Not all records from the OAI were harvested. \n");
			
			if(totalRecordCount!=0) {
				body.append("Total number of records available for harvest =").append(totalRecordCount).append(" \n");
				body.append("Number of records harvested =").append(processedRecordCount).append(" \n");
				
				body.append("Number of records added successfully to MST  = ").append(addedCount+updatedCount).append(" \n");
				if (failedInserts !=0) {
					body.append("Number of records failed =").append(failedInserts).append("\n\n");
				}
			} else if (processedRecordCount > 0) {
				body.append("Number of records harvested =").append(processedRecordCount).append(" \n");
				
				body.append("Number of records added successfully to MST  = ").append(addedCount+updatedCount).append(" \n");
				if (failedInserts !=0) {
					body.append("Number of records failed =").append(failedInserts).append("\n\n");
				}
				
			} 
			if (runningHarvester.recordsFound == 0) {
				body.append("There are no records available for harvest. \n");
			}
			
	
			// Show the log information for warnings and errors
			if(errors.length() > 0)
				body.append("The following errors occurred:\n").append(errors.toString()).append(warnings.length() > 0 ? "\n" : "");
			if(warnings.length() > 0)
				body.append("The following warnings were generated:\n").append(warnings.toString());
	
			return mailer.sendEmail(schedule.getNotifyEmail(), subject, body.toString());
		} else {
			return false;
		}
	} // end method sendReportEmail
		

	/**
	 * Retrieves an OAI XML document via http and parses the XML.
	 *
	 * @param request The http request, e.g., "http://www.x.com/..."
	 * @return The doc value
	 * @exception Hexception If an error occurs
	 */
	private Document getDoc(String request)throws Hexception
	{
		if(log.isInfoEnabled())
			log.info("Sending the OAI request: " + request);

		Document doc = null;

		GetMethod getOaiResponse = null;

		try
		{
			int statusCode = 0; // The status code in the HTTP response

			startOaiRequest = System.currentTimeMillis();

			TimingLogger.start("http sent and received");
			synchronized(client)
			{
				// Instantiate a GET HTTP method to get the Voyager "first" page
				
				getOaiResponse = new GetMethod(request);

				// Execute the get method to get the Voyager "first" page
				statusCode = client.executeMethod(getOaiResponse);
			}

	        // If the get was successful (200 is the status code for success)
	        if(statusCode == 200)
	        {
	        	InputStream istm = getOaiResponse.getResponseBodyAsStream();
	        	TimingLogger.stop("http sent and received");

	        	/*
	        	String line = null;
	        	StringBuilder sb = new StringBuilder();
	        	BufferedReader reader = new BufferedReader(new InputStreamReader(istm, "UTF-8"));
        	    while ((line = reader.readLine()) != null) {
        	        sb.append(line).append("\n");
        	    }
        	    */
	        	//System.out.println(sb.toString());
	        	
	        	finishOaiRequest = System.currentTimeMillis();
	        	

	            log.info("Time taken to get a response from the server " + (finishOaiRequest-startOaiRequest));
	            oaiRepositoryTime += (finishOaiRequest-startOaiRequest);
	            log.info("Total time taken to get a response from the server " + oaiRepositoryTime);

				DocumentBuilderFactory docfactory = DocumentBuilderFactory.newInstance();
				docfactory.setCoalescing(true);
				docfactory.setExpandEntityReferences(true);
				docfactory.setIgnoringComments(true);
				docfactory.setNamespaceAware(true);

				// We must set validation false since jdk1.4 parser
				// doesn't know about schemas.
				docfactory.setValidating(false);

				// Ignore whitespace doesn't work unless setValidating(true),
				// according to javadocs.
				docfactory.setIgnoringElementContentWhitespace(false);

				DocumentBuilder docbuilder = docfactory.newDocumentBuilder();

				xmlerrors = "";
				xmlwarnings = "";
				docbuilder.setErrorHandler(this);
				TimingLogger.start("parse");
				doc = docbuilder.parse(istm);
				istm.close();
				TimingLogger.stop("parse");

				if (xmlerrors.length() > 0 || xmlwarnings.length() > 0)
				{
					String msg = "XML validation failed.\n";

					if (xmlerrors.length() > 0)
					{
						msg += "Errors:\n" + xmlerrors;
						LogWriter.addError(schedule.getProvider().getLogFileName(), "The OAI provider's response had the following XML errors:\n" + xmlerrors);
						errorCount++;
					} // end if(error parsing the response)
					if (xmlwarnings.length() > 0)
					{
						msg += "Warnings:\n" + xmlwarnings;
						LogWriter.addWarning(schedule.getProvider().getLogFileName(), "The OAI provider's response had the following XML warnings:\n" + xmlwarnings);
						warningCount++;
					} // end if(warning parsing the response)

					throw new Hexception(msg);
				} // end if(problem parsing the responses)
	        } // end if(status code indicates success)
	        else
	        {
	        	String msg = "Error getting the HTML document, the HTTP status code was " + statusCode;

	        	log.error(msg);

	        	LogWriter.addError(schedule.getProvider().getLogFileName(), msg);
				errorCount++;

				loggedException = true;

				sendReportEmail(msg);

				throw new Hexception(msg);
	        }
		} // end try(place the HTTP request)
		catch (Exception exc)
		{
			log.error("Error getting the HTML document.", exc);

			String msg = "";

			if (exc.getMessage().matches(".*respcode.*"))
				msg = "The request for data resulted in an invalid response from the provider. The baseURL indicated may be incorrect or the service may be unavailable. HTTP response: " + exc.getMessage();
			else if(exc.getMessage().contains("The markup in the document following the root element must be well-formed"))
				msg = "The OAI repository did not return valid XML, so it could not be harvested.";
			else if(exc.getMessage().contains("Read timed out"))
				msg = "The request for data timed out.";
			else
				msg = "The request for data resulted in an invalid response from the provider. Error: " + exc.getMessage();

			LogWriter.addError(schedule.getProvider().getLogFileName(), msg);
			errorCount++;

			loggedException = true;

			sendReportEmail(msg);

			throw new Hexception(msg);
		} // end catch(Exception)
		return doc;
	} // end method getDoc(String)

	/**
	 *  Formats a date as specified in section 3.3 of http://www.openarchives.org/OAI/2.0/openarchivesprotocol.htm.
	 *  <p>
	 *
	 *  If granularity is GRAN_DAY, the format is "yyyy-MM-dd". If granularity is GRAN_SECOND, the format is
	 *  "yyyy-MM-ddTHH:mm:ssZ".
	 *
	 * @param granularity GRAN_DAY or GRAN_SECOND
	 * @param dt The Date
	 * @return An OAI datestamp
	 */
	private String formatDate(int granularity, Date dt)
	{
		TimingLogger.start("formatDate");
		SimpleDateFormat sdf = null;

		if (granularity == ValidateRepository.GRANULARITY_SECOND)
		{
			sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
			sdf.setTimeZone(new SimpleTimeZone(SimpleTimeZone.UTC_TIME, "UTC"));
		} // end if(second granularity is used)
		else
			sdf = new SimpleDateFormat("yyyy-MM-dd");

		String res = sdf.format(dt);

		TimingLogger.stop("formatDate");
		return res;
	} // end method formatDate(int, Date)

	/**
	 * Creates a String from an XML node
	 *
	 * @param node The node to serialize
	 * @return The node in String form
	 */
	private String serialize(Node node)
	{
		TimingLogger.start("serialize");
		try
		{
			OutputFormat format = new OutputFormat();
			StringWriter result = new StringWriter();
			XMLSerializer serializer = new XMLSerializer(result, format);
	        switch (node.getNodeType())
	        {
	        	case Node.DOCUMENT_NODE:
	               serializer.serialize((Document) node);
	               break;
	            case Node.ELEMENT_NODE:
	               serializer.serialize((Element) node);
	               break;
	            case Node.DOCUMENT_FRAGMENT_NODE:
	               serializer.serialize((DocumentFragment) node);
	               break;
	         } // end switch on node type

	        return result.toString();
	    } // end try(serialize the record)
	    catch (Exception e)
	    {
	        log.error("Error: ", e);
	        return null;
	    } finally {
	    	TimingLogger.stop("serialize");
	    }
	} // end method serialize(Node)

	/**
	 * Handles fatal errors. Part of ErrorHandler interface.
	 *
	 * @param  exc  The Exception thrown
	 */
	public void fatalError(SAXParseException exc)
	{
		xmlerrors += exc;
	} // end method fatalError(SAXParseException)

	/**
	 * Handles errors. Part of ErrorHandler interface.
	 *
	 * @param  exc  The Exception thrown
	 */
	public void error(SAXParseException exc)
	{
		xmlerrors += exc;
	} // end method error(SAXParseException)

	/**
	 * Handles warnings. Part of ErrorHandler interface.
	 *
	 * @param  exc  The Exception thrown
	 */
	public void warning(SAXParseException exc)
	{
		xmlwarnings += exc;
	} // end method warning(SAXParseException)

	/**
	 * Gets the reference to the current running harvester
	 * @return The reference to the current running harvester
	 */
	public static Harvester getRunningHarvester() {
		return runningHarvester;
	}

	/**
	 * Gets the pause status of the harvester.
	 */
	public boolean isPaused() {
		return isPaused;
	}

	/**
	 * Sets the pause status of the harvester.
	 */
	public void setPaused(boolean isPaused)
	{
		this.isPaused = isPaused;
	}

	/**
	 * Logs the status of the harvest to the database
	 * @throws DataException
	 */
	protected void persistStatus(String status)
	{
		log.info("Changing the status to " + status);
		schedule.setStatus(status);
		try {
			getHarvestScheduleDAO().update(schedule, false);
		} catch (DataException e) {
			log.error("Error during updating status of harvest_schedule to database.", e);
		}
	}

	/**
	 * Checks if the service is paused or canceled. If canceled,
	 * the processing of records is stopped or else if paused,
	 * then waits until it receives a resume or cancel signal
	 */
	private void checkSignal(String baseURL) {
		if(isPaused) {
			// Update the status of the harvest schedule
			persistStatus(Constants.STATUS_SERVICE_PAUSED);

			while(isPaused && !killed) {
				try {
					log.info("Harvester Paused. Sleeping for 3 secs.");
					Thread.sleep(3000);
				} catch (InterruptedException e) {
					log.info("Harvester Resumed.");
				}
			}
			if (!killed) {
				// Harvester is resumed while paused
				LogWriter.addInfo(schedule.getProvider().getLogFileName(), "Harvest of " + baseURL + " has been resumed.");

				// Update the status of the harvest schedule
				persistStatus(Constants.STATUS_SERVICE_RUNNING);				
			}
		}
			
		if (killed) {
			LogWriter.addInfo(schedule.getProvider().getLogFileName(), "Harvest of " + baseURL + " has been manually terminated.");
			loggedException = true;
			sendReportEmail("Harvest of " + baseURL + " has been manually terminated.");
			throw new RuntimeException("Harvest received kill signal");
		}
	}

	/**
	 * Gets the provider of the harvest
	 */
	public Provider getProvider() {
		return provider;
	}

	/**
	 * Gets the status of the job
	 */
	public String getHarvesterStatus(){
		if (killed)
			return Constants.STATUS_SERVICE_CANCELED;
		else if (isPaused)
			return Constants.STATUS_SERVICE_PAUSED;
		else if (runningHarvester != null)
			return Constants.STATUS_SERVICE_RUNNING;
		else
			return Constants.STATUS_SERVICE_NOT_RUNNING;
	}
	
	/**
	 * Gets the Processed record count
	 * @return
	 */
	public int getProcessedRecordCount() {
		
		return processedRecordCount;
	}

	/**
	 * Reset the Processed record count
	 * @return
	 */
	public static void resetProcessedRecordCount() {
		
		processedRecordCount = 0;
	}

	/**
	 * Gets the total record count
	 * @return
	 */
	public int getTotalRecordCount() {
		
		return  totalRecordCount;
	}
	
	/**
	 * Reset the total record count
	 * @return
	 */
	public static void resetTotalRecordCount() {
		
		totalRecordCount = 0;
	}

	/**
	 * 
	 * @return
	 */
	public boolean isKilled() {
		return killed;
	}
	
	public RecordService getRecordService() {
		return recordService;
	}

	public void setRecordService(RecordService recordService) {
		this.recordService = recordService;
	}

} // end class Harvester