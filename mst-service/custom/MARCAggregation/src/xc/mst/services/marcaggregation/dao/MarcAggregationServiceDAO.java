/**
 * Copyright (c) 2010 eXtensible Catalog Organization
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the MIT/X11 license. The text of the
 * license can be found at http://www.opensource.org/licenses/mit-license.php and copy of the license can be found on the project
 * website http://www.extensiblecatalog.org/.
 *
 */
package xc.mst.services.marcaggregation.dao;


import gnu.trove.TLongLongHashMap;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.mutable.MutableInt;
import org.apache.log4j.Logger;

import xc.mst.services.impl.dao.GenericMetadataServiceDAO;
import xc.mst.utils.MSTConfiguration;
import xc.mst.utils.TimingLogger;

/**
*
* @author John Brand
*
*/
public class MarcAggregationServiceDAO extends GenericMetadataServiceDAO {

    private final static Logger LOG = Logger.getLogger(MarcAggregationServiceDAO.class);

    public final static String bibsProcessedLongId_table = "bibsProcessedLongId";
    public final static String bibsProcessedStringId_table = "bibsProcessedStringId";
    public final static String bibsYet2ArriveLongId_table = "bibsYet2ArriveLongId";
    public final static String bibsYet2ArriveStringId_table = "bibsYet2ArriveStringId";
    public final static String held_holdings_table = "held_holdings";

    public final static String matchpoints_010a_table = "matchpoints_010a";
    public final static String matchpoints_020a_table = "matchpoints_020a";
    public final static String matchpoints_022a_table = "matchpoints_022a";
    public final static String matchpoints_024a_table = "matchpoints_024a";
    public final static String matchpoints_028a_table = "matchpoints_028a";
    public final static String matchpoints_035a_table = "matchpoints_035a";
    public final static String matchpoints_130a_table = "matchpoints_130a";
    public final static String matchpoints_240a_table = "matchpoints_240a";
    public final static String matchpoints_245a_table = "matchpoints_245a";
    public final static String matchpoints_260abc_table = "matchpoints_260abc";



    @SuppressWarnings("unchecked")
    //perhaps will move this up to the generic layer - since 2 services will end up with identical code.
    public void persistBibMaps(
        ) {
        TimingLogger.start("MarcAggregationServiceDAO.persistBibMaps");
        TimingLogger.stop("MarcAggregationServiceDAO.persistBibMaps");
    }

    @SuppressWarnings("unchecked")
    public void persist2StrMatchpointMaps(Map<Long, List<String[]>> inputId2matcherMap, String tableName) {

        TimingLogger.start("MarcAggregationServiceDAO.persist2StrMaps");

        for (Object keyObj : inputId2matcherMap.keySet()) {
            Long id = (Long) keyObj;
            Object list = inputId2matcherMap.get(id);

            try {
                if (list == null) {
                    continue;
                }
                final byte[] idBytes = String.valueOf(id).getBytes();

                String dbLoadFileStr = (MSTConfiguration.getUrlPath() + "/db_load.in").replace('\\', '/');
                final byte[] tabBytes = "\t".getBytes();
                final byte[] newLineBytes = "\n".getBytes();

                File dbLoadFile = new File(dbLoadFileStr);
                if (dbLoadFile.exists()) {
                    dbLoadFile.delete();
                }
                final OutputStream os = new BufferedOutputStream(new FileOutputStream(dbLoadFileStr));
                final MutableInt j = new MutableInt(0);
                TimingLogger.start(tableName + ".insert");
                TimingLogger.start(tableName + ".insert.create_infile");

                List<String[]> strList = (List<String[]>) list;
                LOG.debug("insert: " + tableName + ".size(): " + strList.size());
                if (strList != null && strList.size() > 0) {
                    for (String[] _s: strList) {
                        try {   // need to loop through all strings associated with id!
                                if (j.intValue() > 0) {
                                    os.write(newLineBytes);
                                } else {
                                    j.increment();
                                }
                                os.write(_s[1].getBytes());
                                os.write(tabBytes);
                                os.write(_s[0].getBytes());
                                os.write(tabBytes);
                                os.write(idBytes);
                        } catch (Exception e) {
                            LOG.error("problem with data - ",e);
                        }
                    }
                }
                os.close();
                TimingLogger.stop(tableName + ".insert.create_infile");
                TimingLogger.start(tableName + ".insert.load_infile");
                this.jdbcTemplate.execute(
                        "load data infile '" + dbLoadFileStr + "' REPLACE into table " +
                                tableName +
                                " character set utf8 fields terminated by '\\t' lines terminated by '\\n'"
                        );
                TimingLogger.stop(tableName + ".insert.load_infile");
                TimingLogger.stop(tableName + ".insert");
            } catch (Throwable t) {
                getUtil().throwIt(t);
            }
        }
        TimingLogger.stop("MarcAggregationServiceDAO.persist2StrMaps");
    }

    @SuppressWarnings("unchecked")
    public void persist1StrMatchpointMaps(Map<Long, List<String>> inputId2matcherMap, String tableName) {
        TimingLogger.start("MarcAggregationServiceDAO.persist1StrMatchpointMaps");

        for (Object keyObj : inputId2matcherMap.keySet()) {
            Long id = (Long) keyObj;
            Object list = inputId2matcherMap.get(id);

            try {
                if (list == null) {
                    continue;
                }
                final byte[] idBytes = String.valueOf(id).getBytes();

                String dbLoadFileStr = (MSTConfiguration.getUrlPath() + "/db_load.in").replace('\\', '/');
                final byte[] tabBytes = "\t".getBytes();
                final byte[] newLineBytes = "\n".getBytes();

                File dbLoadFile = new File(dbLoadFileStr);
                if (dbLoadFile.exists()) {
                    dbLoadFile.delete();
                }
                final OutputStream os = new BufferedOutputStream(new FileOutputStream(dbLoadFileStr));
                final MutableInt j = new MutableInt(0);
                TimingLogger.start(tableName + ".insert");
                TimingLogger.start(tableName + ".insert.create_infile");

                List<String> strList = (List<String>) list;
                LOG.debug("insert: " + tableName + ".size(): " + strList.size());
                if (strList != null && strList.size() > 0) {
                    for (String _s: strList) {
                        try {   // need to loop through all strings associated with id!
                                if (j.intValue() > 0) {
                                    os.write(newLineBytes);
                                } else {
                                    j.increment();
                                }
                                os.write(_s.getBytes());
                                os.write(tabBytes);
                                os.write(idBytes);
                        } catch (Exception e) {
                            LOG.error("problem with data - ",e);
                        }
                    }
                }
                os.close();
                TimingLogger.stop(tableName + ".insert.create_infile");
                TimingLogger.start(tableName + ".insert.load_infile");
                this.jdbcTemplate.execute(
                        "load data infile '" + dbLoadFileStr + "' REPLACE into table " +
                                tableName +
                                " character set utf8 fields terminated by '\\t' lines terminated by '\\n'"
                        );
                TimingLogger.stop(tableName + ".insert.load_infile");
                TimingLogger.stop(tableName + ".insert");
            } catch (Throwable t) {
                getUtil().throwIt(t);
            }
        }

        TimingLogger.stop("MarcAggregationServiceDAO.persist1StrMatchpointMaps");
    }

    // this one if for persisting those that do not repeat (1 set of entries per record id) and has a TLongLong and a String for each record id
    @SuppressWarnings("unchecked")
    public void persistLongStrMatchpointMaps(TLongLongHashMap inputId2numMap, Map<Long, String> inputId2matcherMap, String tableName) {

        TimingLogger.start("MarcAggregationServiceDAO.persistLongStrMaps");

        for (Object keyObj : inputId2matcherMap.keySet()) {
            Long id = (Long) keyObj;
            String str = inputId2matcherMap.get(id);
            Long num = inputId2numMap.get(id);
            if (str == null) {
                continue;
            }
            if (num == null) {
                continue;
            }
            try {
                final byte[] idBytes = String.valueOf(id).getBytes();
                final byte[] numBytes = String.valueOf(num).getBytes();

                String dbLoadFileStr = (MSTConfiguration.getUrlPath() + "/db_load.in").replace('\\', '/');
                final byte[] tabBytes = "\t".getBytes();
                final byte[] newLineBytes = "\n".getBytes();

                File dbLoadFile = new File(dbLoadFileStr);
                if (dbLoadFile.exists()) {
                    dbLoadFile.delete();
                }
                final OutputStream os = new BufferedOutputStream(new FileOutputStream(dbLoadFileStr));
                final MutableInt j = new MutableInt(0);
                TimingLogger.start(tableName + ".insert");
                TimingLogger.start(tableName + ".insert.create_infile");
                try {   // need to loop through all strings associated with id!
                    if (j.intValue() > 0) {
                        os.write(newLineBytes);
                    } else {
                        j.increment();
                    }
                    os.write(numBytes);
                    os.write(tabBytes);
                    os.write(str.getBytes());
                    os.write(tabBytes);
                    os.write(idBytes);
                } catch (Exception e) {
                    LOG.error("problem with data - ",e);
                }

                os.close();
                TimingLogger.stop(tableName + ".insert.create_infile");
                TimingLogger.start(tableName + ".insert.load_infile");
                this.jdbcTemplate.execute(
                        "load data infile '" + dbLoadFileStr + "' REPLACE into table " +
                                tableName +
                                " character set utf8 fields terminated by '\\t' lines terminated by '\\n'"
                        );
                TimingLogger.stop(tableName + ".insert.load_infile");
                TimingLogger.stop(tableName + ".insert");
            } catch (Throwable t) {
                getUtil().throwIt(t);
            }
        }
        TimingLogger.stop("MarcAggregationServiceDAO.persistLongStrMaps");
    }

    // this one if for persisting those that do not repeat (1 set of entries per record id) and has a String for each record id
    @SuppressWarnings("unchecked")
    public void persistOneStrMatchpointMaps(Map<Long, String> inputId2matcherMap, String tableName) {

        TimingLogger.start("MarcAggregationServiceDAO.persistOneStrMaps");

        for (Object keyObj : inputId2matcherMap.keySet()) {
            Long id = (Long) keyObj;
            String str = inputId2matcherMap.get(id);
            if (str == null) {
                continue;
            }
            try {
                final byte[] idBytes = String.valueOf(id).getBytes();

                String dbLoadFileStr = (MSTConfiguration.getUrlPath() + "/db_load.in").replace('\\', '/');
                final byte[] tabBytes = "\t".getBytes();
                final byte[] newLineBytes = "\n".getBytes();

                File dbLoadFile = new File(dbLoadFileStr);
                if (dbLoadFile.exists()) {
                    dbLoadFile.delete();
                }
                final OutputStream os = new BufferedOutputStream(new FileOutputStream(dbLoadFileStr));
                final MutableInt j = new MutableInt(0);
                TimingLogger.start(tableName + ".insert");
                TimingLogger.start(tableName + ".insert.create_infile");
                try {   // need to loop through all strings associated with id!
                    if (j.intValue() > 0) {
                        os.write(newLineBytes);
                    } else {
                        j.increment();
                    }
                    os.write(str.getBytes());
                    os.write(tabBytes);
                    os.write(idBytes);
                } catch (Exception e) {
                    LOG.error("problem with data - ",e);
                }

                os.close();
                TimingLogger.stop(tableName + ".insert.create_infile");
                TimingLogger.start(tableName + ".insert.load_infile");
                this.jdbcTemplate.execute(
                        "load data infile '" + dbLoadFileStr + "' REPLACE into table " +
                                tableName +
                                " character set utf8 fields terminated by '\\t' lines terminated by '\\n'"
                        );
                TimingLogger.stop(tableName + ".insert.load_infile");
                TimingLogger.stop(tableName + ".insert");
            } catch (Throwable t) {
                getUtil().throwIt(t);
            }
        }
        TimingLogger.stop("MarcAggregationServiceDAO.persistOneStrMaps");
    }

    protected List<Map<String, Object>> getMaps(String tableName, int page) {
//        TimingLogger.start("getMaps");
        int recordsAtOnce = 250000;
        List<Map<String, Object>> rowList =null;//= this.jdbcTemplate.queryForList(
//                "select org_code, bib_001, record_id from " + tableName +
//                        " limit " + (page * recordsAtOnce) + "," + recordsAtOnce);
//        TimingLogger.stop("getMaps");
        return rowList;
    }

    @SuppressWarnings("unchecked")
    public void loadMaps(
        ) {
    }
}
