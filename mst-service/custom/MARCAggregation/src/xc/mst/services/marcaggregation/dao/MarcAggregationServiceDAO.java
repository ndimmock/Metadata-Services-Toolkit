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
import gnu.trove.TLongLongProcedure;
import gnu.trove.TLongObjectHashMap;
import gnu.trove.TLongObjectProcedure;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.mutable.MutableInt;
import org.apache.log4j.Logger;

import org.springframework.jdbc.core.RowMapper;

import xc.mst.services.impl.dao.GenericMetadataServiceDAO;
import xc.mst.services.marcaggregation.RecordOfSourceData;
import xc.mst.utils.MSTConfiguration;
import xc.mst.utils.TimingLogger;
/**
*
* @author John Brand
*
*/
public class MarcAggregationServiceDAO extends GenericMetadataServiceDAO {

    private final static Logger LOG = Logger.getLogger(MarcAggregationServiceDAO.class);

    public final static String bibsProcessedLongId_table    = "bibsProcessedLongId";
    public final static String bibsProcessedStringId_table  = "bibsProcessedStringId";
    public final static String bibsYet2ArriveLongId_table   = "bibsYet2ArriveLongId";
    public final static String bibsYet2ArriveStringId_table = "bibsYet2ArriveStringId";
    public final static String held_holdings_table          = "held_holdings";

    public final static String matchpoints_010a_table   = "matchpoints_010a";
    public final static String matchpoints_020a_table   = "matchpoints_020a";
    public final static String matchpoints_022a_table   = "matchpoints_022a";
    public final static String matchpoints_024a_table   = "matchpoints_024a";
    public final static String matchpoints_028a_table   = "matchpoints_028a";
    public final static String matchpoints_035a_table   = "matchpoints_035a";
    public final static String matchpoints_130a_table   = "matchpoints_130a";
    public final static String matchpoints_240a_table   = "matchpoints_240a";
    public final static String matchpoints_245a_table   = "matchpoints_245a";
    public final static String matchpoints_260abc_table = "matchpoints_260abc";

    public final static String merge_scores_table       = "merge_scores";
    public final static String merged_records_table     = "merged_records";

    public final static String input_record_id_field    = "input_record_id";
    public final static String string_id_field          = "string_id";
    public final static String numeric_id_field         = "numeric_id";
    public final static String leaderByte17_field       = "leaderByte17";
    public final static String size_field               = "size";


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
                String dbLoadFileStr = getDbLoadFileStr();

                final byte[] idBytes = String.valueOf(id).getBytes();
                final byte[] tabBytes = getTabBytes();
                final byte[] newLineBytes = getNewLineBytes();

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
                            getUtil().throwIt(e);
                        }
                    }
                }
                os.close();
                replaceIntoTable(tableName, dbLoadFileStr);
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
                String dbLoadFileStr = getDbLoadFileStr();

                final byte[] idBytes = String.valueOf(id).getBytes();
                final byte[] tabBytes = getTabBytes();
                final byte[] newLineBytes = getNewLineBytes();
                final OutputStream os = new BufferedOutputStream(new FileOutputStream(dbLoadFileStr));
                final MutableInt j = new MutableInt(0);
                TimingLogger.start(tableName + ".insert");
                TimingLogger.start(tableName + ".insert.create_infile");

                List<String> strList = (List<String>) list;
                LOG.debug("insert: " + tableName + ".size(): " + strList.size());
                if (strList != null && strList.size() > 0) {
                    for (String _s: strList) {
                        if (StringUtils.isEmpty(_s)) {
                            continue;
                        }
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
                            getUtil().throwIt(e);
                        }
                    }
                }
                os.close();
                replaceIntoTable(tableName, dbLoadFileStr);
            } catch (Throwable t) {
                getUtil().throwIt(t);
            }
        }
        TimingLogger.stop("MarcAggregationServiceDAO.persist1StrMatchpointMaps");
    }

    /**
     * this one if for persisting those that do not repeat (1 set of entries per record id) and has a TLongLong only for each record id
     * ,also using it to persist merged_records, input_record->output_record
     *
     * @param inputId2numMap
     * @param tableName
     * @param swap - if true, then write the key / value as value / key into the db
     */
    public void persistLongMatchpointMaps(TLongLongHashMap inputId2numMap, String tableName, final boolean swap) {

        TimingLogger.start("MarcAggregationServiceDAO.persistLongMaps");
        try {

            String dbLoadFileStr = getDbLoadFileStr();
            final OutputStream os = new BufferedOutputStream(new FileOutputStream(dbLoadFileStr));
            final MutableInt j = new MutableInt(0);
            TimingLogger.start(tableName + ".insert");
            TimingLogger.start(tableName + ".insert.create_infile");

            final byte[] tabBytes = getTabBytes();
            final byte[] newLineBytes = getNewLineBytes();

            if (inputId2numMap instanceof TLongLongHashMap) {
                LOG.debug("insert: " + tableName + ".size(): " + inputId2numMap.size());
                if (inputId2numMap != null && inputId2numMap.size() > 0) {
                    inputId2numMap.forEachEntry(new TLongLongProcedure() {
                        public boolean execute(long id, long num) {
                            try {
                                if (j.intValue() > 0) {
                                    LOG.debug("line break!!! j:" + j.intValue());
                                    os.write(newLineBytes);
                                } else {
                                    j.increment();
                                }
                                if (swap) {        // write value then key
                                    os.write(String.valueOf(num).getBytes());
                                    os.write(tabBytes);
                                    os.write(String.valueOf(id).getBytes());
                                }
                                else {             // write key then value
                                    os.write(String.valueOf(id).getBytes());
                                    os.write(tabBytes);
                                    os.write(String.valueOf(num).getBytes());
                                }
                            } catch (Throwable t) {
                                getUtil().throwIt(t);
                            }
                            return true;
                        }
                    });
                }
            }
            os.close();
            replaceIntoTable(tableName, dbLoadFileStr);
        } catch (Throwable t) {
            TimingLogger.stop("MarcAggregationServiceDAO.persistLongMaps");
            getUtil().throwIt(t);
        }
        TimingLogger.stop("MarcAggregationServiceDAO.persistLongMaps");
    }

    // this one if for persisting those that do not repeat (1 set of entries per record id) and has a TLongLong and a String for each record id
    public void persistLongStrMatchpointMaps(TLongLongHashMap inputId2numMap, Map<Long, String> inputId2matcherMap, String tableName) {

        TimingLogger.start("MarcAggregationServiceDAO.persistLongStrMaps");

        for (Object keyObj : inputId2matcherMap.keySet()) {
            Long id = (Long) keyObj;
            if (id == null) {
                // should not be.
                LOG.error("****   persistLongStrMatchpointMaps, problem with data, id,- id="+id);
                continue;
            }
            String str = inputId2matcherMap.get(id);
            Long num = inputId2numMap.get(id);
            if (StringUtils.isEmpty(str)) {
                LOG.error("****   persistLongStrMatchpointMaps, problem with data, str,- id="+id);
                continue;
            }
            if (num == null) {
                // should not be.
                LOG.error("****   persistLongStrMatchpointMaps, problem with data, num,- id="+id);
                continue;
            }
            try {
                String dbLoadFileStr = getDbLoadFileStr();

                final byte[] idBytes = String.valueOf(id).getBytes();
                final byte[] numBytes = String.valueOf(num).getBytes();
                final byte[] tabBytes = getTabBytes();
                final byte[] newLineBytes = getNewLineBytes();

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
                    LOG.error("*** problem with data - recordid="+id,e);
                    getUtil().throwIt(e);
                }
                os.close();
                replaceIntoTable(tableName, dbLoadFileStr);
            } catch (org.springframework.dao.DataIntegrityViolationException t2) {
                LOG.error("****   problem with data - num="+num+" str="+str+" id="+id,t2);
                getUtil().throwIt(t2);
            } catch (Throwable t) {
                getUtil().throwIt(t);
            }
        }
        TimingLogger.stop("MarcAggregationServiceDAO.persistLongStrMaps");
    }

    // this one if for persisting those that do not repeat (1 set of entries per record id) and has a String for each record id
    public void persistOneStrMatchpointMaps(Map<Long, String> inputId2matcherMap, String tableName) {

        TimingLogger.start("MarcAggregationServiceDAO.persistOneStrMaps");

        for (Object keyObj : inputId2matcherMap.keySet()) {
            Long id = (Long) keyObj;
            String str = inputId2matcherMap.get(id);
            if (StringUtils.isEmpty(str)) {
                continue;
            }
            try {
                String dbLoadFileStr = getDbLoadFileStr();

                final byte[] idBytes = String.valueOf(id).getBytes();
                final byte[] tabBytes = getTabBytes();
                final byte[] newLineBytes = getNewLineBytes();
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
                    getUtil().throwIt(e);
                }

                os.close();
                replaceIntoTable(tableName, dbLoadFileStr);
            } catch (Throwable t) {
                getUtil().throwIt(t);
            }
        }
        TimingLogger.stop("MarcAggregationServiceDAO.persistOneStrMaps");
    }

    public void persistScores(TLongObjectHashMap<xc.mst.services.marcaggregation.RecordOfSourceData> scores) {

        final String tableName = merge_scores_table;
        TimingLogger.start("MarcAggregationServiceDAO.persistScores");
        try {
            String dbLoadFileStr = getDbLoadFileStr();

            final byte[] tabBytes = getTabBytes();
            final byte[] newLineBytes = getNewLineBytes();

            final OutputStream os = new BufferedOutputStream(new FileOutputStream(dbLoadFileStr));
            final MutableInt j = new MutableInt(0);
            TimingLogger.start(tableName + ".insert");
            TimingLogger.start(tableName + ".insert.create_infile");

            if (scores instanceof TLongObjectHashMap) {
                LOG.debug("insert: " + tableName + ".size(): " + scores.size());
                if (scores != null && scores.size() > 0) {
                    scores.forEachEntry(new TLongObjectProcedure<xc.mst.services.marcaggregation.RecordOfSourceData>() {
                        public boolean execute(long id, xc.mst.services.marcaggregation.RecordOfSourceData source) {
                            try {
                                if (j.intValue() > 0) {
                                    LOG.debug("line break!!! j:" + j.intValue());
                                    os.write(newLineBytes);
                                } else {
                                    j.increment();
                                }
                                os.write(String.valueOf(id).getBytes());
                                os.write(tabBytes);
                                os.write(String.valueOf(source.leaderByte17).getBytes());
                                os.write(tabBytes);
                                os.write(String.valueOf(source.size).getBytes());
                            } catch (Throwable t) {
                                getUtil().throwIt(t);
                            }
                            return true;
                        }
                    });
                }
            }
            os.close();
            replaceIntoTable(tableName, dbLoadFileStr);
        } catch (Throwable t) {
            getUtil().throwIt(t);
        } finally {
            TimingLogger.stop("MarcAggregationServiceDAO.persistScores");
        }
    }

    protected static byte[] getTabBytes() {
        return "\t".getBytes();
    }

    protected static byte[] getNewLineBytes() {
        return "\n".getBytes();
    }

    // not only does it create the string but it has a side effect - it creates a file from the string,
    // checks for its existence and deletes it if it finds it.
    protected String getDbLoadFileStr() {
        String dbLoadFileStr =
        (MSTConfiguration.getUrlPath() + "/db_load.in").replace('\\', '/');

        File dbLoadFile = new File(dbLoadFileStr);
        if (dbLoadFile.exists()) {
            dbLoadFile.delete();
        }
        return dbLoadFileStr;
    }

    protected void replaceIntoTable(String tableName, String dbLoadFileStr) {
        TimingLogger.stop(tableName + ".insert.create_infile");
        TimingLogger.start(tableName + ".insert.load_infile");
        this.jdbcTemplate.execute(
                "load data infile '" + dbLoadFileStr + "' REPLACE into table " +
                        tableName +
                        " character set utf8 fields terminated by '\\t' lines terminated by '\\n'"
                );
        TimingLogger.stop(tableName + ".insert.load_infile");
        TimingLogger.stop(tableName + ".insert");
    }

    /**
     *   merged_records
     *   purpose: provides a mapping of input records to output records. This allows for 2 paths:
     *
     *     -------------------------------------------------------------------
     *     | given               | can be determined                           |
     *     |-------------------------------------------------------------------|
     *     | an output_record_id | all the input_records that have been merged |
     *     |                     | together to create this output_record       |
     *     |-------------------------------------------------------------------|
     *     | an input_record_id  | all the other input_records that have been  |
     *     |                     | merged with this input_record and the       |
     *     |                     | corresponding output_record                 |
     *      -------------------------------------------------------------------
     *
     * @param output_record_id
     * @return all the input_records that have been merged together to create this output_record
     */
    public List<Long> getInputRecordsMergedToOutputRecord(Long output_record_id) {
        String sql = "select input_record_id from " + merged_records_table +
                            " where output_record_id = ? ";
        return this.jdbcTemplate.queryForList(sql, Long.class, output_record_id);
    }

    /**
     * what output record corresponds to this input record?
     * @param input_record_id
     * @return there will only be 1 record number returned.
     */
    public List<Long> getOutputRecordForInputRecord(Long input_record_id) {
        String sql = "select output_record_id from " + merged_records_table +
                            " where input_record_id = ? ";
        return this.jdbcTemplate.queryForList(sql, Long.class, input_record_id);
    }

    public TLongLongHashMap getMergedRecords(/*int page*/) {
        TimingLogger.start("getMergedRecords");

//        int recordsAtOnce = 100000;
        String sql = "select input_record_id, output_record_id from " + merged_records_table;// +
//                " limit " + (page * recordsAtOnce) + "," + recordsAtOnce;

        List<Map<String, Object>> rowList = this.jdbcTemplate.queryForList(sql);
        TLongLongHashMap results = new TLongLongHashMap();
        for (Map<String, Object> row : rowList) {
            Long in_id = (Long) row.get("input_record_id");
            Long out_id = (Long) row.get("output_record_id");
            results.put(in_id, out_id);
        }
        TimingLogger.stop("getMergedRecords");
        return results;
    }

    public RecordOfSourceData getScoreData(Long num) {
        TimingLogger.start("getMatchingRecords");

        final String tableName = merge_scores_table;

        String sql = "select "+ leaderByte17_field +", "+ size_field +
                " from " + tableName+ " where "+ input_record_id_field +" = ?";

        List<RecordOfSourceData> rowList = this.jdbcTemplate.query(sql, new Object[] {num}, new RecordOfSourceDataMapper());

        TimingLogger.stop("getMatchingRecords");

        final int size = rowList.size();
        if (size == 0) {
            LOG.error("No rows returned for merge_scores for "+num);
            return null;
        }
        else if (size>1) {
            // enforce through schema?
            LOG.error("multiple rows returned for merge_scores for "+num);
        }
        return rowList.get(0);
    }

    private static final class RecordOfSourceDataMapper implements RowMapper<RecordOfSourceData> {

        public RecordOfSourceData mapRow(ResultSet rs, int rowNum) throws SQLException {
            RecordOfSourceData source;
            char encoding;

            // the ' ' is not getting into the db. Is it a big deal, or is this
            // hack good enough?
            //
            if (StringUtils.isNotEmpty(rs.getString("leaderByte17"))) {
                encoding = rs.getString("leaderByte17").charAt(0);
            }
            else encoding=' ';

            source = new RecordOfSourceData(encoding,rs.getInt("size"));
            return source;
        }
    }

    // given a string_id in String form to match on.
    //
    // for instance:
    //mysql -u root --password=root -D xc_marcaggregation -e 'select input_record_id  from matchpoints_035a where string_id = "24094664" '
    //
    public List<Long> getMatchingRecords(String tableName, String record_id_field, String string_id_field, String itemToMatch) {
        TimingLogger.start("getMatchingRecords");

        String sql = "select "+ record_id_field + " from " + tableName+ " where "+ string_id_field +" = ?";

        List<Map<String, Object>> rowList = this.jdbcTemplate.queryForList(sql, new Object[] {itemToMatch});

        List<Long> results = new ArrayList<Long>();
        for (Map<String, Object> row : rowList) {
            Long id = (Long) row.get("input_record_id");
            results.add(id);
        }
        TimingLogger.stop("getMatchingRecords");
        return results;
    }

    // given a numeric_id in Long form to match on.
    //
    // for instance:
    //mysql -u root --password=root -D xc_marcaggregation -e 'select input_record_id  from matchpoints_035a where string_id = "24094664" '
    //
    public List<Long> getMatchingRecords(String tableName, String record_id_field, String string_id_field, Long itemToMatch) {
        TimingLogger.start("getMatchingRecords");

        String sql = "select "+ record_id_field + " from " + tableName+ " where "+ numeric_id_field +" = ?";

        List<Map<String, Object>> rowList = this.jdbcTemplate.queryForList(sql, new Object[] {itemToMatch});

        List<Long> results = new ArrayList<Long>();
        for (Map<String, Object> row : rowList) {
            Long id = (Long) row.get("input_record_id");
            results.add(id);
        }
        TimingLogger.stop("getMatchingRecords");
        return results;
    }

    public int getNumRecords(String tableName) {
        return this.jdbcTemplate.queryForInt("select count(*) from " + tableName);
    }

    public int getNumUniqueStringIds(String tableName) {
        return this.jdbcTemplate.queryForInt("select count(distinct string_id) from " + tableName);
    }

    public int getNumUniqueNumericIds(String tableName) {
        return this.jdbcTemplate.queryForInt("select count(distinct numeric_id) from " + tableName);
    }

    public int getNumUniqueRecordIds(String tableName) {
        return this.jdbcTemplate.queryForInt("select count(distinct input_record_id) from " + tableName);
    }

    public void loadMaps(
        ) {
    }
}
