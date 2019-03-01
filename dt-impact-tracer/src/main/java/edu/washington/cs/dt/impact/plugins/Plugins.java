package edu.washington.cs.dt.impact.plugins;

import com.reedoei.testrunner.mavenplugin.TestPluginPlugin;
import edu.washington.cs.dt.impact.runner.OneConfigurationRunner;
import edu.washington.cs.dt.impact.runner.Runner;
import edu.washington.cs.dt.main.ImpactMain;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.cli.MavenCli;
import org.apache.maven.project.MavenProject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.Map;
import java.util.Objects;

public class Plugins {
    protected enum Mode {DEBUG, NORMAL}

    protected String projectName;

    // Old version main Directories
    protected String dtSubjectSource;
    protected String dtTools;
    protected String dtLibs;
    protected String dtClass;
    protected String dtTests;

    // Old version output Directories
    protected String dtResults;
    protected String prioResults;
    protected String paraResults;
    private String dtData;
    protected String prioDTLists;
    protected String seleDTLists;
    protected String paraDTLists;


    // New Version Main Directories
    protected String newDTSubjectSource;
    protected String newDTTools;
    protected String newDTLibs;
    protected String newDTClass;
    protected String newDTTests;

    // New Version Output Directories
    protected String newDTResults, newPrioResults, newSeleResults, newParaResults;

    // Constants
    protected final int MEDIANTIMES = 5;

    protected final String[] TESTTYPES = { "orig" };
    protected final String[] COVERGAES = { "statement", "function" };
    protected String[] MACHINES = { "2", "4", "8", "16"};

    protected final String[] PRIOORDERS = { "absolute", "relative" };
    protected final String[] SELEORDERS = { "original", "absolute", "relative"};


    protected void setupNewVers(MavenProject project) {
        setupPaths(project, System.getProperty("path"));

        // New Version
        newDTSubjectSource = System.getProperty("user.dir");

        String newDTSubject = newDTSubjectSource.concat("/target");
        new File(newDTSubject).mkdirs();
        newDTTools = buildClassPath(newDTSubjectSource.concat("/lib/*"));
        newDTLibs = buildClassPath(newDTSubject.concat("/dependency/*"));
        newDTClass = newDTSubject.concat("/classes");
        newDTTests = newDTSubject.concat("/test-classes");

        newDTResults = newDTSubjectSource.concat("/results");
        FileUtils.deleteQuietly(new File(newDTResults));
        new File(newDTResults).mkdirs();
        newPrioResults = newDTResults.concat("/prioritization-results");
        new File(newPrioResults).mkdirs();
        newSeleResults = newDTResults.concat("/selection-results");
        new File(newSeleResults).mkdirs();
        newParaResults = newDTResults.concat("/parallelization-results");
        new File(newParaResults).mkdirs();
    }


    protected void setupOldVers(MavenProject project) {
        setupPaths(project, System.getProperty("user.dir"));

        new File(prioResults).mkdirs();
        new File(paraResults).mkdirs();

        new File(dtData).mkdirs();
        new File(paraDTLists).mkdirs();
        new File(prioDTLists).mkdirs();
        new File(seleDTLists).mkdirs();
    }

    private void setupPaths(MavenProject project, String dtSubjectSource){
        this.dtSubjectSource = dtSubjectSource;
        projectName = project.getArtifactId().replace('-','.');

        String dtSubject = dtSubjectSource.concat("/target");
        new File(dtSubject).mkdirs();

        String libsDir = project.getProperties().getProperty("dt.lib.dir");
        if (libsDir == null || libsDir.isEmpty()) {
            throw new RuntimeException("Could not find necessary dependencies for plugin. " +
                                               "Please see the following link to download and setup the necessary libs." +
                                               "https://sites.google.com/view/test-dependence-impact/guidelines");
        }
        dtTools = buildClassPath(libsDir + "/*");
        dtLibs = buildClassPath(dtSubject.concat("/dependency/*"));
        dtClass = dtSubject.concat("/classes");
        dtTests = dtSubject.concat("/test-classes");

        dtResults = dtSubjectSource.concat("/results");
        new File(dtResults).mkdirs();

        String dtOutput = dtResults.concat("/output");
        new File(dtOutput).mkdirs();

        // Output Directories for old version
        prioResults = dtResults.concat("/prioritization-results");
        paraResults = dtResults.concat("/parallelization-results");

        // DT lists in old version
        dtData = dtResults.concat("/data");
        prioDTLists = dtData.concat("/prioritization-dt-lists");
        seleDTLists = dtData.concat("/selection-dt-lists");
        paraDTLists = dtData.concat("/parallelization-dt-lists");
    }


    // Compile & Gather The Dependencies Of A Subject
    public void gatherDependencies(MavenCli client, String subjectPath){
        TestPluginPlugin.info("Gathering Dependencies Of The Subject");

        client.doMain(new String[]{"compile",
                "test-compile",
                "-Dmaven.javadoc.skip=true",
                "-DskipTests",
                "dependency:copy-dependencies",
                "-Drat.skip=true",
                "-Dcobertura.skip" }, subjectPath, System.out, System.out);
        client.doMain(new String[]{"install",
                "-fn",
                "-Dmaven.javadoc.skip=true",
                "-DskipTests",
                "dependency:copy-dependencies",
                "-Drat.skip=true",
                "-Dcobertura.skip" }, subjectPath, System.out, System.out);
    }

    // Adjust MACHINES
    public String[] adjustMachineCount(String[] machines, String testOrderPath){
        int numInvalidEntries = 0;
        for (String num : machines){
            try {
                if ((int) Files.lines(new File(testOrderPath).toPath()).count() < Integer.parseInt(num)){
                    numInvalidEntries++;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        String[] temp = new String[machines.length - numInvalidEntries];
        for (int i = 0; i < temp.length; i++){
            temp[i] = machines[i];
        }

        return temp;
    }

    /**
     * This function builds a classpath from the passed string
     * From https://stackoverflow.com/questions/4752817/expand-environment-variables-in-text
     *
     * @param text environment variable
     * @return returns the complete classpath with the environment_variable expanded
     */
    public String expandEnvVars(String text) {
        Map<String, String> envMap = System.getenv();
        for (Map.Entry<String, String> entry : envMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            text = text.replaceAll("\\$\\{" + key + "\\}", value);
        }
        return text;
    }

    /**
     * This function builds a classpath from the passed Strings
     * From https://stackoverflow.com/questions/22965975/how-to-set-up-classpath-of-javacompiler-to-multiple-jar-files-using-wildcard
     *
     * @param paths classpath elements
     * @return returns the complete classpath with wildcards expanded
     */
    public String buildClassPath(String... paths) {
        StringBuilder sb = new StringBuilder();
        for (String path : paths) {
            if (path.endsWith("*")) {
                path = path.substring(0, path.length() - 1);
                File pathFile = new File(path);
                pathFile.mkdirs();
                System.out.println(path);
                for (File file : Objects.requireNonNull(pathFile.listFiles())) {
                    if (file.isFile() && file.getName().endsWith(".jar")) {
                        sb.append(path);
                        sb.append(file.getName());
                        sb.append(System.getProperty("path.separator"));
                    }
                }
            } else {
                sb.append(path);
                sb.append(System.getProperty("path.separator"));
            }
        }
        return sb.toString();
    }


    protected void runOneConfigurationRunner(String[] args,
                                                    String outputFile) {
        String paramsString = "OneConfigurationRunner Parameters\n\t" + StringUtils.join(args, "\n\t");
        if (outputFile == null) {
            TestPluginPlugin.info(paramsString);
            Runner.nullOutputFileName();
            OneConfigurationRunner.main(args);
        } else {
            PrintStream stdout = System.out;
            try {
                PrintStream out = new PrintStream(new FileOutputStream(outputFile));
                System.setOut(out);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            Runner.nullOutputFileName();
            System.out.println(paramsString);
            OneConfigurationRunner.main(args);
            System.setOut(stdout);
        }
    }

    protected void runImpactMain(String[] args,
                                        String outputFile) {
        if (outputFile == null) {
            ImpactMain.main(args);
        } else {
            PrintStream stdout = System.out;
            try {
                PrintStream out = new PrintStream(new FileOutputStream(outputFile));
                System.setOut(out);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            ImpactMain.main(args);
            System.setOut(stdout);
        }
    }


    protected void printProcessMessages(Process p, Mode runMode, String subprocessOutput) throws IOException {
        if (runMode == Mode.DEBUG) {
            // Stream Readers
            BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));

            // Read Command Output
            TestPluginPlugin.info("Command Input Stream For: ");
            while ((subprocessOutput = stdInput.readLine()) != null) {
                TestPluginPlugin.info(subprocessOutput);
            }

            // Read Command Errors
            TestPluginPlugin.info("Command Error: Stream For: ");
            TestPluginPlugin.info(subprocessOutput);
            while ((subprocessOutput = stdError.readLine()) != null) {
                TestPluginPlugin.mojo().getLog().error(subprocessOutput);
            }
        }
    }
}
