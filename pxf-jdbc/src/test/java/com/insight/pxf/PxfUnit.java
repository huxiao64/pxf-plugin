package com.insight.pxf;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Assert;

import org.apache.hawq.pxf.api.Fragment;
import org.apache.hawq.pxf.api.Fragmenter;
import org.apache.hawq.pxf.api.OneField;
import org.apache.hawq.pxf.api.OneRow;
import org.apache.hawq.pxf.api.ReadAccessor;
import org.apache.hawq.pxf.api.ReadResolver;
import org.apache.hawq.pxf.api.WriteAccessor;
import org.apache.hawq.pxf.api.WriteResolver;
import org.apache.hawq.pxf.api.io.DataType;
import org.apache.hawq.pxf.api.utilities.InputData;
import org.apache.hawq.pxf.service.FragmentsResponse;
import org.apache.hawq.pxf.service.FragmentsResponseFormatter;
import org.apache.hawq.pxf.service.utilities.ProtocolData;

/**
 * This abstract class contains a number of helpful utilities in developing a
 * PXF extension for HAWQ. Extend this class and use the various
 * <code>assert</code> methods to check given input against known output.
 */
public abstract class PxfUnit {

    private static JsonFactory factory = new JsonFactory();
    protected static ObjectMapper mapper = new ObjectMapper(factory);

    //master的输入数据
    protected InputData masterInputData = null;
    //每个分片的输入数据
    protected List<InputData> frag_inputs = null;

    public void cleanup() throws Exception {
        frag_inputs.clear();
        masterInputData = null;
        mapper = new ObjectMapper(factory);
    }
    /**
     * Uses the given input directory to run through the PXF unit testing
     * framework. Uses the lines in the file for output testing.
     *
     * @param input
     *            Input records
     * @param expectedOutput
     *            File containing output to check
     * @throws Exception
     */
    public void assertOutput(String input, Path expectedOutput) throws Exception {

        BufferedReader rdr = new BufferedReader(new InputStreamReader(
                FileSystem.get(new Configuration()).open(expectedOutput)));

        List<String> outputLines = new ArrayList<String>();

        String line;
        while ((line = rdr.readLine()) != null) {
            outputLines.add(line);
        }

        assertOutput(input, outputLines);
    }

    /**
     * Uses the given input directory to run through the PXF unit testing
     * framework. Uses the lines in the given parameter for output testing.
     *
     * @param input
     *            Input records
     * @param expectedOutput
     *            File containing output to check
     * @throws Exception
     */
    public void assertOutput(String input, List<String> expectedOutput)
            throws Exception {

        setup(input, null);
        List<String> actualOutput = getFragmentsOut(frag_inputs);

        Assert.assertFalse("Output did not match expected output",
                compareOutput(expectedOutput, actualOutput));
    }

    //检查分区数据是否完整
    public void assertFragmentsOutput(String input, String filterString, int expectedRows) throws Exception {
        setup(input, filterString);

        //查询全表数据
        List<String> expectedOutput = getAllOutput();
        Assert.assertEquals("数据未全部遍历.",expectedRows,expectedOutput.size());

        //分区读取数据
        List<String> actualOutput = getFragmentsOut(frag_inputs);

        //比较
        Assert.assertFalse("Output did not match expected output",
                compareUnorderedOutput(expectedOutput, actualOutput));

    }

    protected List<String> getFragmentsOut(List<InputData> frag_inputs) throws Exception {
        List<String> actualOutput = new ArrayList<String>();
        for (InputData data : frag_inputs) {
            ReadAccessor accessor = getReadAccessor(data);
            ReadResolver resolver = getReadResolver(data);

            actualOutput.addAll(getOutputData(accessor, resolver));
        }
        return actualOutput;
    }

    /**
     * Uses the given input directory to run through the PXF unit testing
     * framework. Uses the lines in the given parameter for output testing.<br>
     * <br>
     * Ignores order of records.
     *
     * @param input
     *            Input records
     * @param expectedOutput
     *            File containing output to check
     * @throws Exception
     */
    public void assertUnorderedOutput(String input, Path expectedOutput)
            throws Exception {
        BufferedReader rdr = new BufferedReader(new InputStreamReader(
                FileSystem.get(new Configuration()).open(expectedOutput)));

        List<String> outputLines = new ArrayList<String>();

        String line;
        while ((line = rdr.readLine()) != null) {
            outputLines.add(line);
        }

        assertUnorderedOutput(input, outputLines);
    }

    /**
     * Uses the given input directory to run through the PXF unit testing
     * framework. Uses the lines in the file for output testing.<br>
     * <br>
     * Ignores order of records.
     *
     * @param input
     *            Input records
     * @param expectedOutput
     *            File containing output to check
     * @throws Exception
     */
    public void assertUnorderedOutput(String input, List<String> expectedOutput)
            throws Exception {

        setup(input, null);

        List<String> actualOutput = getFragmentsOut(frag_inputs);

        Assert.assertFalse("Output did not match expected output",
                compareUnorderedOutput(expectedOutput, actualOutput));
    }

    /**
     * Writes the output to the given output stream. Comma delimiter.
     *
     * @param input
     *            The input file
     * @param output
     *            The output stream
     * @throws Exception
     */
    public void writeOutput(String input, OutputStream output) throws Exception {

        setup(input, null);

        for (InputData data : frag_inputs) {
            ReadAccessor accessor = getReadAccessor(data);
            ReadResolver resolver = getReadResolver(data);

            for (String line : getOutputData(accessor, resolver)) {
                output.write((line + "\n").getBytes());
            }
        }

        output.flush();
    }

    /**
     * Get the class of the implementation of Fragmenter to be tested.
     *
     * @return The class
     */
    public Class<? extends Fragmenter> getFragmenterClass() {
        return null;
    }

    /**
     * Get the class of the implementation of ReadAccessor to be tested.
     *
     * @return The class
     */
    public Class<? extends ReadAccessor> getReadAccessorClass() {
        return null;
    }

    /**
     * Get the class of the implementation of WriteAccessor to be tested.
     *
     * @return The class
     */
    public Class<? extends WriteAccessor> getWriteAccessorClass() {
        return null;
    }

    /**
     * Get the class of the implementation of Resolver to be tested.
     *
     * @return The class
     */
    public Class<? extends ReadResolver> getReadResolverClass() {
        return null;
    }

    /**
     * Get the class of the implementation of WriteResolver to be tested.
     *
     * @return The class
     */
    public Class<? extends WriteResolver> getWriteResolverClass() {
        return null;
    }

    /**
     * Get any extra parameters that are meant to be specified for the "pxf"
     * protocol. Note that "X-GP-" is prepended to each parameter name.
     *
     * @return Any extra parameters or null if none.
     */
    public List<Pair<String, String>> getExtraParams() {
        return null;
    }

    /**
     * Gets the column definition names and data types. Types are DataType
     * objects
     *
     * @return A list of column definition name value pairs. Cannot be null.
     */
    public abstract List<Pair<String, DataType>> getColumnDefinitions();

    protected InputData getInputDataForWritableTable() {
        return getInputDataForWritableTable(null);
    }

    protected InputData getInputDataForWritableTable(String input) {

        if (getWriteAccessorClass() == null) {
            throw new IllegalArgumentException(
                    "getWriteAccessorClass() must be overwritten to return a non-null object");
        }

        if (getWriteResolverClass() == null) {
            throw new IllegalArgumentException(
                    "getWriteResolverClass() must be overwritten to return a non-null object");
        }

        Map<String, String> paramsMap = new HashMap<String, String>();

        paramsMap.put("X-GP-ALIGNMENT", "what");
        paramsMap.put("X-GP-SEGMENT-ID", "1");
        paramsMap.put("X-GP-HAS-FILTER", "0");
        paramsMap.put("X-GP-SEGMENT-COUNT", "1");

        paramsMap.put("X-GP-FORMAT", "GPDBWritable");
        paramsMap.put("X-GP-URL-HOST", "localhost");
        paramsMap.put("X-GP-URL-PORT", "50070");

        if (input == null) {
            paramsMap.put("X-GP-DATA-DIR", "/dummydata");
        }

        List<Pair<String, DataType>> params = getColumnDefinitions();
        paramsMap.put("X-GP-ATTRS", Integer.toString(params.size()));
        for (int i = 0; i < params.size(); ++i) {
            paramsMap.put("X-GP-ATTR-NAME" + i, params.get(i).first);
            paramsMap
                    .put("X-GP-ATTR-TYPENAME" + i, params.get(i).second.name());
            paramsMap.put("X-GP-ATTR-TYPECODE" + i,
                    Integer.toString(params.get(i).second.getOID()));
        }

        paramsMap.put("X-GP-ACCESSOR", getWriteAccessorClass().getName());
        paramsMap.put("X-GP-RESOLVER", getWriteResolverClass().getName());

        if (getExtraParams() != null) {
            for (Pair<String, String> param : getExtraParams()) {
                paramsMap.put("X-GP-" + param.first, param.second);
            }
        }

        return new ProtocolData(paramsMap);
    }

    /**
     * Set all necessary parameters for GPXF framework to function. Uses the
     * given path as a single input split.
     *
     * @param input
     *            The input path, relative or absolute.
     * @param filterString
     * @throws Exception
     */
    protected void setup(String input, String filterString) throws Exception {
        if (input == null) input = "/dummydata";

        Assert.assertNotNull( "getFragmenterClass() must be overwritten to return a non-null object",getFragmenterClass());
        Assert.assertNotNull( "getReadAccessorClass() must be overwritten to return a non-null object",getReadAccessorClass());
        Assert.assertNotNull( "getReadResolverClass() must be overwritten to return a non-null object",getReadResolverClass());

        Map<String, String> paramsMap = new HashMap<String, String>();

        // 2.1.0 Properties
        // HDMetaData parameters
        if(filterString == null || filterString.equals("")) {
            paramsMap.put("X-GP-HAS-FILTER", "0");
        }else{
            paramsMap.put("X-GP-HAS-FILTER", "1");
            paramsMap.put("X-GP-FILTER", filterString);
        }
        paramsMap.put("X-GP-ALIGNMENT", "what");
        paramsMap.put("X-GP-SEGMENT-ID", "1");
        paramsMap.put("X-GP-SEGMENT-COUNT", "1");
        paramsMap.put("X-GP-FRAGMENTER", getFragmenterClass().getName());
        paramsMap.put("X-GP-FORMAT", "GPDBWritable");
        paramsMap.put("X-GP-URL-HOST", "localhost");
        paramsMap.put("X-GP-URL-PORT", "50070");

        paramsMap.put("X-GP-DATA-DIR", input);

        List<Pair<String, DataType>> params = getColumnDefinitions();
        paramsMap.put("X-GP-ATTRS", Integer.toString(params.size()));
        for (int i = 0; i < params.size(); ++i) {
            paramsMap.put("X-GP-ATTR-NAME" + i, params.get(i).first);
            paramsMap
                    .put("X-GP-ATTR-TYPENAME" + i, params.get(i).second.name());
            paramsMap.put("X-GP-ATTR-TYPECODE" + i,
                    Integer.toString(params.get(i).second.getOID()));
        }

        // HDFSMetaData properties
        paramsMap.put("X-GP-ACCESSOR", getReadAccessorClass().getName());
        paramsMap.put("X-GP-RESOLVER", getReadResolverClass().getName());

        if (getExtraParams() != null) {
            for (Pair<String, String> param : getExtraParams()) {
                paramsMap.put("X-GP-" + param.first, param.second);
            }
        }

        masterInputData = new LocalInputData(paramsMap);

        List<Fragment> fragments = getFragmenter(masterInputData)
                .getFragments();

        FragmentsResponse fragmentsResponse = FragmentsResponseFormatter
                .formatResponse(fragments, input.toString());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        fragmentsResponse.write(baos);

        String jsonOutput = baos.toString();

        frag_inputs = new ArrayList<InputData>();

        JsonNode node = decodeLineToJsonNode(jsonOutput);

        JsonNode fragmentsArray = node.get("PXFFragments");
        int i = 0;
        Iterator<JsonNode> iter = fragmentsArray.getElements();
        while (iter.hasNext()) {
            JsonNode fragNode = iter.next();
            String sourceData = fragNode.get("sourceName").getTextValue();
           // if (!sourceData.startsWith("/")) {
            //    sourceData = "/" + sourceData;
           // }
            paramsMap.put("X-GP-DATA-DIR", sourceData);
            paramsMap.put("X-GP-FRAGMENT-METADATA", fragNode.get("metadata")
                    .getTextValue());
            paramsMap.put("X-GP-DATA-FRAGMENT", Integer.toString(i++));
            frag_inputs.add(new LocalInputData(paramsMap));
        }
    }

    private JsonNode decodeLineToJsonNode(String line) {

        try {
            return mapper.readTree(line);
        } catch (JsonParseException e) {
            e.printStackTrace();
            return null;
        } catch (JsonMappingException e) {
            e.printStackTrace();
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Compares the expected and actual output, printing out any errors.
     *
     * @param expectedOutput
     *            The expected output
     * @param actualOutput
     *            The actual output
     * @return True if no errors, false otherwise.
     */
    protected boolean compareOutput(List<String> expectedOutput,
                                    List<String> actualOutput) {

        boolean error = false;
        for (int i = 0; i < expectedOutput.size(); ++i) {
            boolean match = false;
            for (int j = 0; j < actualOutput.size(); ++j) {
                if (expectedOutput.get(i).equals(actualOutput.get(j))) {
                    match = true;
                    if (i != j) {
                        System.err.println("Expected (" + expectedOutput.get(i)
                                + ") matched (" + actualOutput.get(j)
                                + ") but in wrong place.  " + j
                                + " instead of " + i);
                        error = true;
                    }

                    break;
                }
            }

            if (!match) {
                System.err.println("Missing expected output: ("
                        + expectedOutput.get(i) + ")");
                error = true;
            }
        }

        for (int i = 0; i < actualOutput.size(); ++i) {
            boolean match = false;
            for (int j = 0; j < expectedOutput.size(); ++j) {
                if (actualOutput.get(i).equals(expectedOutput.get(j))) {
                    match = true;
                    break;
                }
            }

            if (!match) {
                System.err.println("Received unexpected output: ("
                        + actualOutput.get(i) + ")");
                error = true;
            }
        }

        return error;
    }

    /**
     * Compares the expected and actual output, printing out any errors.
     *
     * @param expectedOutput
     *            The expected output
     * @param actualOutput
     *            The actual output
     * @return True if no errors, false otherwise.
     */
    protected boolean compareUnorderedOutput(List<String> expectedOutput,
                                             List<String> actualOutput) {

        boolean error = false;
        for (int i = 0; i < expectedOutput.size(); ++i) {
            boolean match = false;
            for (int j = 0; j < actualOutput.size(); ++j) {
                if (expectedOutput.get(i).equals(actualOutput.get(j))) {
                    match = true;
                    break;
                }
            }

            if (!match) {
                System.err.println("Missing expected output: ("
                        + expectedOutput.get(i) + ")");
                error = true;
            }
        }

        for (int i = 0; i < actualOutput.size(); ++i) {
            boolean match = false;
            for (int j = 0; j < expectedOutput.size(); ++j) {
                if (actualOutput.get(i).equals(expectedOutput.get(j))) {
                    match = true;
                    break;
                }
            }

            if (!match) {
                System.err.println("Received unexpected output: ("
                        + actualOutput.get(i) + ")");
                error = true;
            }
        }

        return error;
    }

    /**
     * Opens the accessor and reads all output, giving it to the resolver to
     * retrieve the list of fields. These fields are then added to a string,
     * delimited by commas, and returned in a list.
     *
     * @param accessor
     *            The accessor instance to use
     * @param resolver
     *            The resolver instance to use
     * @return The list of output strings
     * @throws Exception
     */
    protected List<String> getOutputData(ReadAccessor accessor,
                                         ReadResolver resolver) throws Exception {

        Assert.assertTrue("Accessor failed to open", accessor.openForRead());

        List<String> output = new ArrayList<String>();

        OneRow row = null;
        while ((row = accessor.readNextObject()) != null) {

            StringBuilder bldr = new StringBuilder();
            for (OneField field : resolver.getFields(row)) {
                bldr.append((field != null && field.val != null ? field.val
                        : "") + ",");
            }

            if (bldr.length() > 0) {
                bldr.deleteCharAt(bldr.length() - 1);
            }

            output.add(bldr.toString());
        }

        accessor.closeForRead();

        return output;
    }

    protected List<String> getAllOutput() throws Exception {
        ReadAccessor accessor = getReadAccessor(masterInputData);
        ReadResolver resolver = getReadResolver(masterInputData);
        return getOutputData(accessor,resolver);
    }

    /**
     * Gets an instance of Fragmenter via reflection.
     *
     * Searches for a constructor that has a single parameter of some
     * BaseMetaData type
     *
     * @return A Fragmenter instance
     * @throws Exception
     *             If something bad happens
     */
    protected Fragmenter getFragmenter(InputData meta) throws Exception {

        Fragmenter fragmenter = null;

        for (Constructor<?> c : getFragmenterClass().getConstructors()) {
            if (c.getParameterTypes().length == 1) {
                for (Class<?> clazz : c.getParameterTypes()) {
                    if (InputData.class.isAssignableFrom(clazz)) {
                        fragmenter = (Fragmenter) c.newInstance(meta);
                    }
                }
            }
        }

        if (fragmenter == null) {
            throw new InvalidParameterException(
                    "Unable to find Fragmenter constructor with a BaseMetaData parameter");
        }

        return fragmenter;

    }

    /**
     * Gets an instance of ReadAccessor via reflection.
     *
     * Searches for a constructor that has a single parameter of some InputData
     * type
     *
     * @return An ReadAccessor instance
     * @throws Exception
     *             If something bad happens
     */
    protected ReadAccessor getReadAccessor(InputData data) throws Exception {

        ReadAccessor accessor = null;

        for (Constructor<?> c : getReadAccessorClass().getConstructors()) {
            if (c.getParameterTypes().length == 1) {
                for (Class<?> clazz : c.getParameterTypes()) {
                    if (InputData.class.isAssignableFrom(clazz)) {
                        accessor = (ReadAccessor) c.newInstance(data);
                    }
                }
            }
        }

        if (accessor == null) {
            throw new InvalidParameterException(
                    "Unable to find Accessor constructor with a BaseMetaData parameter");
        }

        return accessor;

    }

    /**
     * Gets an instance of IFieldsResolver via reflection.
     *
     * Searches for a constructor that has a single parameter of some
     * BaseMetaData type
     *
     * @return A IFieldsResolver instance
     * @throws Exception
     *             If something bad happens
     */
    protected ReadResolver getReadResolver(InputData data) throws Exception {

        ReadResolver resolver = null;

        // search for a constructor that has a single parameter of a type of
        // BaseMetaData to create the accessor instance
        for (Constructor<?> c : getReadResolverClass().getConstructors()) {
            if (c.getParameterTypes().length == 1) {
                for (Class<?> clazz : c.getParameterTypes()) {
                    if (InputData.class.isAssignableFrom(clazz)) {
                        resolver = (ReadResolver) c.newInstance(data);
                    }
                }
            }
        }

        if (resolver == null) {
            throw new InvalidParameterException(
                    "Unable to find Resolver constructor with a BaseMetaData parameter");
        }

        return resolver;
    }

    public static class Pair<FIRST, SECOND> {

        public FIRST first;
        public SECOND second;

        public Pair() {
        }

        public Pair(FIRST f, SECOND s) {
            this.first = f;
            this.second = s;
        }
    }

    /**
     * An extension of InputData for the local file system instead of HDFS.
     * Leveraged by the PXFUnit framework. Do not concern yourself with such a
     * simple piece of code.
     */
    public static class LocalInputData extends ProtocolData {

        public LocalInputData(ProtocolData copy) {
            super(copy);
            super.setDataSource(super.getDataSource().substring(1));
        }

        public LocalInputData(Map<String, String> paramsMap) {
            super(paramsMap);
        }
    }
}
