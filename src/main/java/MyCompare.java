
import com.sun.org.apache.xalan.internal.xsltc.cmdline.getopt.GetOpt;
import com.sun.org.apache.xalan.internal.xsltc.cmdline.getopt.GetOptsException;
// This uses an internal package which may be discontinued in the future.
// A public version is in https://alvinalexander.com/java/jwarehouse/netbeans-src/javacvs/libsrc/org/netbeans/lib/cvsclient/commandLine/GetOpt.java.shtml

import java.io.File;  // Import the File class
import java.io.FileInputStream;
import java.io.FileNotFoundException;  // Import this class to handle errors
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Scanner; // Import the Scanner class to read text files
//import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static javax.xml.bind.DatatypeConverter.printHexBinary;

public class MyCompare {
    private PrintStream result; // target
    private PrintStream trace; // target
    private SearchEngine library; // target
    private String topDirectory; // target
    private SearchEngine sourceLibrary; // readonly
    private String filterFile;
    private Hash hash;
    private String stamp;
    private int numberOfPast;
    private int numberOfChanges;
    private boolean ignorePastChecksum;
    private static String standardTraceName = "-trace-";
    private static String standardLibraryName = "-library-";
    private static String standardSolutionName = "-trace-solution-";

    public static boolean verbose = false;

    private File standardFile( String top, String filetype )
    {
        File standardName = new File(top, "." + hash.getName() + filetype + stamp + ".txt");
        return standardName;
    }

    private File standardTrace( String top )
    {
        return standardFile( top, standardTraceName );
    }

    private File standardLibrary( String top )
    {
        return standardFile( top, standardLibraryName );
    }

    private File standardSolution( String top ) { return standardFile( top, standardSolutionName ); }

    private boolean isStandardLibrary( String name )
    {
        return name.startsWith( "." + hash.getName() + standardLibraryName );
    }

    private void initialize()
    {
        java.text.DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");
        Date date = new Date();
        stamp = dateFormat.format(date);
        try {
            Charset cs = Charset.defaultCharset();
            String defaultEncoding = System.getProperty("file.encoding");
            trace = new PrintStream(standardTrace(topDirectory));
            if( trace != null && defaultEncoding != null) trace.println("file.encoding="+defaultEncoding);
            if( trace != null && filterFile != null) trace.println("filter="+filterFile);
            if( ! defaultEncoding.equalsIgnoreCase("UTF-8"))
                trace.println("Default encoding is "+defaultEncoding+". If you expect file names in non-English, please set it to UTF-8.");
        } catch (FileNotFoundException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        } finally {
        }
    }

    MyCompare( String _topDirectory, String _filterFile, Hash _hash, boolean _ignorePastChecksum )
    {
        filterFile = _filterFile;
        topDirectory = _topDirectory;
        hash = _hash;
        numberOfPast = numberOfChanges = 0;
        ignorePastChecksum = _ignorePastChecksum;
    }

    private SearchEngine loadChecksum(String directory, String childPrefixTailPath )
    {
        SearchEngine newLibrary = null;
        File top = new File( directory );
        File[] list = top.listFiles();

        if( list==null ) {
            if( trace != null ) trace.println("Directory Not Readable: " + top.getAbsolutePath());
            return null;
        }
        long timestamp = 0;
        File past = null;
        for( int k=0; list != null && k<list.length; ++k ) {
            String childName = list[k].getName();
              if( isStandardLibrary(childName) ) {
                long thisTimeStamp = list[k].lastModified();
                if( thisTimeStamp > timestamp ) {
                    timestamp = thisTimeStamp;
                    past = list[k];
                }
            }
        }
        if( past==null ) {
            if( trace != null && verbose ) trace.println("--- Has not found any earlier checksum library. ");
            return null;
        }
        try {
            if( trace != null ) trace.print("--- Found an earlier checksum library : "+past.getAbsolutePath());
            Scanner pastResult = new Scanner(past);
//            Scanner pastResult = new Scanner(past, "UTF-8");
            newLibrary = new SearchEngine(null, directory, File.separator );
            newLibrary.loadFromFile(pastResult, childPrefixTailPath);
            pastResult.close();
            if( trace != null ) trace.println(", number of files : "+ newLibrary.numberOfFiles());
        } catch (FileNotFoundException e) {
            System.err.println("Cannot find file "+ past.getAbsolutePath());
        }
        return newLibrary;
    }

    /*
    void loadSourceChecksum(String sourceDirectory)
    {
        library = loadChecksum( sourceDirectory );
    }

     */

    public void decendFolder(Hash hash, FileFilter filter, File file, int level, ArrayList<SearchEngine> past, String prefixTailPath, SearchEngine resultLibrary ) throws Exception
    {
        if( file.exists() && file.isFile() )
        {
            if( file.canRead() && file.exists() && ! file.isHidden() ) {
                try {
                    FileRecord rec = null;
                    String tailPath = FileRecord.getTailPath(file.getAbsolutePath(), topDirectory, library.separator);
                    /*
                    byte[] bs = tailPath.getBytes();
                    if( bs.length != tailPath.length()) {
                        trace.print(bs.length + "=");
                        trace.write(bs);
                        trace.print("=" + tailPath.length() + "=");
                        trace.printf(Locale.CHINESE, tailPath);
                        trace.println();
                    }*/

                    for( int k=0; k<level; ++k) {
                        SearchEngine pastLibrary = past.get(k);
                        if( pastLibrary != null ) {
                            rec = (null == tailPath ? null : pastLibrary.findByTailPath(tailPath));
                            if (null != rec && file.length() == rec.size && file.lastModified() == rec.lastModifiedDate) {
                                numberOfPast++; // use this record and don't calculate checkcum again.
                                pastLibrary.oneUsage();
                                break;
                            } else {
                                rec = null; // ignore this past record and calculate checksum again.
                            }
                        }
                    }
                    if( null==rec ) {
                        numberOfChanges++;
                        rec = new FileRecord(file.length(), file.lastModified(), hash.getName(), printHexBinary(Hash.SHA256.checksum(file)), tailPath, FileRecord.FIRSTTIME);
//                        if( numberOfChanges<10 ) System.out.println(rec.toString(topDirectory));
                    }
                    resultLibrary.add(rec);
                } catch ( java.io.FileNotFoundException e ) {
                    if( trace != null) trace.println("Access is denied: " + file.getAbsolutePath() );
                } catch ( Exception e ) {
                    if( e.getCause().getMessage().startsWith("java.io")) {
                        if (trace != null)
                            trace.println("Cannot read file: " + file.getAbsolutePath());
                    } else
                        throw(e);
                }
            } else {
                if (trace != null && ! file.canRead() ) trace.println("File Not readable: " + file.getAbsolutePath());
                if (trace != null && ! file.exists() ) trace.println("File does not exist: " + file.getAbsolutePath());
                if (trace != null && file.isHidden() ) trace.println("File is hidden: " + file.getAbsolutePath());
            }
        } else if( file.exists() && file.isDirectory() )
        {
            // Find and if any, load the latest library
            String childPrefixTailPath = level==0?null:(level==1?file.getName():prefixTailPath+File.separator+file.getName());
            SearchEngine childLibrary = ignorePastChecksum?null:loadChecksum(file.getAbsolutePath(),childPrefixTailPath );
            if( level < past.size() )
                past.set(level, childLibrary );
            else
                past.add(childLibrary);
            // If top level (0), create new library.
            if( level==0 ) {
                result = new PrintStream(standardLibrary(topDirectory));
                library = new SearchEngine(result, topDirectory, File.separator);
            }
            File[] list = file.listFiles();
            if( list==null && trace != null )
                trace.println( "Directory Not Readable: " + file.getAbsolutePath() );
            for( int k=0; list != null && k<list.length; ++k ) {
                if( null==filter || filter.accept(list[k].getParentFile(), list[k].getName()) ) {
                    File childDir = list[k];
                    decendFolder(hash, filter, childDir, level+1, past, childPrefixTailPath, null==resultLibrary?library:resultLibrary);
                } else
                    if( trace != null && verbose ) trace.println( "Ignored: " + list[k].getAbsolutePath() );
            }
            SearchEngine pastLibrary = past.get(level);
            if( pastLibrary != null )
                pastLibrary.reportUsage(trace);
        }
    }

    public void report()
    {
        if( null != trace ) trace.println("--- In total, loaded "+numberOfPast+" file checksum from the past libraries.");
        if( null != trace ) trace.println("--- Calculated "+numberOfChanges+" file checksum.");
        library.reportStatus(trace);
    }

    void decend(String pastDirectory)
    {
        FileFilter filter = (null==filterFile ? null : new FileFilter(filterFile));
        File top = new File( topDirectory );
        try {
            ArrayList<SearchEngine> past = new ArrayList<>();
            decendFolder(hash, filter, top, 0, past, null, null);
            report();
            if (result != null) result.close();
            if (trace != null) trace.close();
        } catch (Exception e) {
            System.out.println( e.getMessage() );
            e.printStackTrace();
        } finally {
        }
    }

    void extract(String sourceDirectory)
    {
        SearchEngine sourceLibrary = (null==sourceDirectory?null:loadChecksum(sourceDirectory, null));

        FileFilter filter = (null==filterFile ? null : new FileFilter(filterFile));
        File top = new File( topDirectory );
        try {
            result = new PrintStream( standardLibrary(topDirectory) );
            library = new SearchEngine(result, topDirectory,File.separator);
            numberOfPast = library.extract(sourceLibrary);
            report();
            if (result != null) result.close();
            if (trace != null) trace.close();
        } catch (Exception e) {
            System.out.println( e.getMessage() );
            e.printStackTrace();
        } finally {
        }
    }

    void archive(String sourceDirectory)
    {
        File archive = new File( topDirectory );
        if( ! archive.exists() )
            archive.mkdirs();
        initialize();
        SearchEngine masterLibrary = (null==sourceDirectory?null:loadChecksum(sourceDirectory, null));
        SearchEngine archivePastLibrary = (null==topDirectory?null:loadChecksum(topDirectory, null));
        try {
            result = new PrintStream(standardLibrary(topDirectory));
            library = new SearchEngine(result, topDirectory, File.separator);

            PrintStream solution = new PrintStream( standardSolution(topDirectory) );
            int numUpdated = masterLibrary.archiveTo(archivePastLibrary, library, solution);
            if( trace != null ) trace.println( "Total files archived: "+numUpdated);
            int numCarried = library.carryOverFrom(archivePastLibrary);
            if( trace != null ) trace.println( "Total files carried over: "+numCarried);
//            report();
            if (solution != null) solution.close();
            if (result != null) result.close();
            if (trace != null) trace.close();
        } catch (Exception e) {
            System.out.println( e.getMessage() );
            e.printStackTrace();
        } finally {
        }
    }

    public static void processHiddenFile(File hidden, File targetDir ) {
        if (hidden.exists() && hidden.isFile()) {
            System.out.println("File " + hidden.getAbsolutePath() + " " + (hidden.canRead() ? "Readalbe" : "Non-Readable") + " " +
                    (hidden.exists() ? "Exist" : "Non-Exist") + " " +
                    (hidden.isHidden() ? "Hidden" : "Shown"));
            System.out.println("Size " + hidden.length() + " " +
                    "Modified " + (new Date(hidden.lastModified())).toString() + " " +
                    "Hashcode " + hidden.hashCode());
            System.out.print("Type c to copy it, d to delete it:");
            try {
                String answer = (null==System.console()) ?
                    "h" : System.console().readLine();
                if( answer.startsWith("c")) {
                    FileInputStream in = new FileInputStream(hidden);
                    File targetFile = new File( targetDir, "hidden_" + hidden.getName());
                    FileOutputStream out = new FileOutputStream(targetFile);
                    byte[] buf = new byte[4096];
                    int s;
                    while ( (s=in.read(buf)) > 0) {
                        out.write(buf,0, s);
                    }
                    in.close();
                    out.close();
                    targetFile.setLastModified(hidden.lastModified());
                    System.out.println(hidden.getAbsolutePath()+ " copied to "+targetFile.getAbsolutePath());
                } else if( answer.startsWith("d")) {
                    hidden.delete();
                    System.out.println(hidden.getAbsolutePath()+" Deleted.");
                }
                System.out.println("-------------------------------------------------------------");
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        } else {
            System.out.println( hidden.getAbsolutePath()+" does not exist or is not a file.");
        }
    }

    public static void findHiddenFiles(FileFilter filter, File file, File recycler, int level)
    {
        if( file.exists() && file.isFile() )
        {
            if(file.isHidden() ) {
                try {
                    processHiddenFile( file, recycler );
                } catch ( Exception e ) {
                    if( e.getCause().getMessage().startsWith("java.io")) {
                      System.out.println("Cannot read file: " + file.getAbsolutePath());
                    } else
                        throw(e);
                }
            }
        } else if( file.exists() && file.isDirectory() )
        {
            File[] list = file.listFiles();
            if( list==null )
                System.out.println( "Directory Not Readable: " + file.getAbsolutePath() );
            for( int k=0; list != null && k<list.length; ++k ) {
                if( null==filter || filter.accept(list[k].getParentFile(), list[k].getName()) ) {
                    findHiddenFiles(filter, list[k], recycler, level+1);
                }
            }
        }
    }

    // This variable should be set to false to prevent any methods in this
    // class from calling System.exit(). As this is a command-line tool,
    // calling System.exit() is normally OK, but we also want to allow for
    // this class being used in other ways as well.
    private static boolean _allowExit = true;

    private static void printUsage()
    {
        System.out.println( "MyCompare, a JAVA program that calculates checksum for files and use them to compare archived files.");
        System.out.println( "  Options:");
        System.out.println( "  -c  Load old checksum results and calculate (-t) checksum for new or changed files.");
        System.out.println( "  -r  ReCalculate checksum (-t) without loading old checksum results.");
        System.out.println( "  -e  Extract checksum from old checksum results (-s) for a specified directory (-t).");
        System.out.println( "  -H  process hidden file (-s) and copy them to a specified directory (-t).");
        System.out.println( "  -s  Source top directory.");
        System.out.println( "  -t  Target top directory.");
        System.out.println( "  -v  Print more tracking information.");
    }

    /*
      Sample commands:

      # calculate checksum from the files, if past checkcum result file is available and size and last modified date match,
      #   do not calculate it again by copying the checksum from past.
      -c -t "C:\Users\Public\Documents" -f "C:\Users\myuid\Documents\Winmerge-user-exclude1.flt" -h SHA-256
      -t "C:\Users\Public\Documents" -f "C:\Users\myuid\Documents\Winmerge-user-exclude1.flt" -h SHA-256
      -t "W:\\Archives" -f "C:\Users\myuid\Documents\Winmerge-user-exclude1.flt" -h SHA-256

      # extract the new (target -t) top directory from a past (source -s) checksum result file. The new top directory is under the (past) source directory.
      -e -s "C:\Users\Public\Documents" -t "C:\Users\Public\Documents\2011-11-11N.dup52" -f "C:\Users\myuid\Documents\Winmerge-user-exclude1.flt" -h SHA-256

      # move a past (source -s) checksum to a higher directory (-t). The new top directory above the (past) source directory.
      -m -s "C:\Users\Public\Documents\images" -t "C:\Users\Public\Documents" -f "C:\Users\myuid\Documents\Winmerge-user-exclude1.flt" -h SHA-256

      # archive a directory (-s) to another (-t), with both checksum calculated already.
      -a -s "C:\Users\Public\Documents\2011-11-11N.dup53" -t "C:\Users\Public\Documents\2011-11-11N.dup53 - Copy" -f "C:\Users\myuid\Documents\Winmerge-user-exclude1.flt" -h SHA-256

      # recalculate checksum from the files, ignoring any past checksum results.
      -r -t "C:\Users\Public\Documents" -f "C:\Users\myuid\Documents\Winmerge-user-exclude1.flt" -h SHA-256

      # Test cases
      -e -s "C:\Users\Public\Documents" -t "C:\Users\Public\Documents\2011-11-11N.dup52" -f "C:\Users\myuid\Documents\Winmerge-user-exclude1.flt" -h SHA-256
      -c -t "C:\Users\Public\Documents\2011-11-11N.dup52" -f "C:\Users\myuid\Documents\Winmerge-user-exclude1.flt" -h SHA-256
         -t "C:\Users\Public\Documents\2011-11-11N.dup52" -f "C:\Users\myuid\Documents\Winmerge-user-exclude1.flt" -h SHA-256
      -r -t "C:\Users\Public\Documents\2011-11-11N.dup52" -f "C:\Users\myuid\Documents\Winmerge-user-exclude1.flt" -h SHA-256
      -c -t "E:\MyUsrId-Storage\Archives" -f "C:\Users\myuid\Documents\Winmerge-user-exclude1.flt" -h SHA-256
      -a -s "C:\Users\Public\Documents\2011-11-11N.dup53" -t "C:\Users\Public\Documents\2011-11-11N.dup53 - Copy" -f "C:\Users\myuid\Documents\Winmerge-user-exclude1.flt" -h SHA-256

     */
    public static void main(String[] args) {
        Hash usedHash = Hash.SHA256;
        boolean calculateNewChecksum = true; // using old checksum if available
        boolean reCalculateChecksum = false; // ignore old checksum
        boolean extractTopDirectory = false; // extract top directory from the calculated checksum, the source and the new (target) top directory are provided by -s and -t options.
        boolean archiveTopDirectory = false; // archive the files from source (-s) top directory to target (-t) directory.
        String sourceDirectory = null;
        String filterFile = null;
        String targetDirectory = null;
        boolean processHidden = false;

        try {
            final GetOpt opt = new GetOpt(args, "creavHs:f::t:h::?::");
            int c;
            String arg;
            arg = System.getProperty("MyCompare.Hash");
            if( arg != null ) {
                usedHash = Hash.getByName(arg);
            }
            filterFile = System.getProperty("MyCompare.filter");

            while ((c = opt.getNextOption()) != -1)
            {
                switch(c)
                {
                    case 's':
                        sourceDirectory = opt.getOptionArg();
                        break;
                    case 'f':
                        filterFile = opt.getOptionArg();
                        break;
                    case 'h':
                        arg = opt.getOptionArg();
                        usedHash = Hash.getByName(arg);
                        break;
                    case 't':
                        targetDirectory = opt.getOptionArg();
                        break;
                    case 'c':
                        calculateNewChecksum = true;
                        reCalculateChecksum = extractTopDirectory = archiveTopDirectory = false;
                        break;
                    case 'r':
                        reCalculateChecksum = true;
                        calculateNewChecksum = extractTopDirectory = archiveTopDirectory = false;
                        break;
                    case 'e':
                        extractTopDirectory = true;
                        calculateNewChecksum = reCalculateChecksum = archiveTopDirectory = false;
                        break;
                    case 'a':
                        archiveTopDirectory = true;
                        calculateNewChecksum = reCalculateChecksum = extractTopDirectory = false;
                        break;
                    case 'v':
                        verbose = true;
                        break;
                    case '?':
                        printUsage();
                        break; // getopt() already printed an error
                    case 'H':
                        processHidden = true;
                        break;
                    default:
                        System.out.print("getopt() returned " + c + " unexpected, exiting.\n");
                        if (_allowExit) System.exit(-1);
                }
            }
        } catch (GetOptsException ex) {
            System.err.println(ex);
            printUsage(); // exits with code '-1'
        } catch (Exception e) {
            e.printStackTrace();
            if (_allowExit) System.exit(-1);
        }
        if( null==targetDirectory ) {
            System.err.println("Target directory has to be specified. Use -t option.");
            if (_allowExit) System.exit(-1);
        }

        if( processHidden && sourceDirectory != null ) {
            FileFilter filter = (null==filterFile ? null : new FileFilter(filterFile));
            File file = new File(sourceDirectory );
            File recycler = (null==targetDirectory ? null : new File(targetDirectory));
            findHiddenFiles(filter, file, recycler, 0);
            return;
        }

        MyCompare engine = new MyCompare( targetDirectory, filterFile, usedHash, reCalculateChecksum?true:false );
        if( calculateNewChecksum || reCalculateChecksum ) {
            sourceDirectory = null;
            engine.initialize();
            engine.decend(targetDirectory);
        } else if( extractTopDirectory ) {
            engine.initialize();
            engine.extract(sourceDirectory);
        } else if( archiveTopDirectory ) {
            engine.archive(sourceDirectory);
        }
    }
}
