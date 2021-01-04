import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.CopyOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.regex.Pattern;

public class SearchEngine {
    PrintStream result;
    public String topDirectory;
    public String separator;
    private ArrayList<FileRecord> allRecords;
    private HashMap<Long, ArrayList<Integer>> bySize;
    private HashMap<String, ArrayList<Integer>> byCheckSum;
    private HashMap<String, ArrayList<Integer>> byTailPath;
    private int usage;
    private static int actionLevel = 0;

    public void oneUsage() { usage++; }
    public void reportUsage( PrintStream trace ) { if( trace != null ) trace.println("--- Loaded "+usage+" file checksum from the past library in "+topDirectory+".");}

    // Create a file record search engine for later adding and optionally saving them
    // to a file with a print stream.
    public SearchEngine(PrintStream _result, String _top, String _separator) {
        topDirectory = _top;
        separator = _separator;
        result = _result;
        allRecords = new ArrayList<FileRecord>();
        byTailPath = new HashMap<String, ArrayList<Integer>>();
        bySize = new HashMap<Long, ArrayList<Integer>>();
        byCheckSum = new HashMap<String, ArrayList<Integer>>();
        if (result != null )
            result.println("-1," + separator);
        usage = 0;
    }

    public int numberOfFiles() {
        return allRecords == null ? 0 : allRecords.size();
    }
    public FileRecord findByTailPath(String path) {
        ArrayList<Integer> found = byTailPath.get(path);
        if (null == found) {
            return null;
        } else {
            return allRecords.get(found.get(0));
        }
    }

    public void add(FileRecord r) {
        Integer index = allRecords.size();
        allRecords.add(r);
        if (result != null) {
            String s = r.toString(null);
//            result.printf(Locale.CHINESE, s);
            result.println(s);
            /*
            byte[] bs = r.toBytes(null);
            try {
                result.write(bs);
                result.println();
            } catch (IOException e) {
                result.println("Write failed");
            }
             */
        }
        ArrayList<Integer> found = byTailPath.get(r.tailPath);
        if (found == null) {
            ArrayList<Integer> newSet = new ArrayList<Integer>();
            newSet.add(index);
            byTailPath.put(r.tailPath, newSet);
        } else {
            found.add(index);
        }

        found = byCheckSum.get(r.checksum);
        if (found == null) {
            ArrayList<Integer> newSet = new ArrayList<Integer>();
            newSet.add(index);
            byCheckSum.put(r.checksum, newSet);
        } else {
            found.add(index);
        }

        found = bySize.get(r.size);
        if (found == null) {
            ArrayList<Integer> newSet = new ArrayList<Integer>();
            newSet.add(index);
            bySize.put(r.size, newSet);
        } else {
            found.add(index);
        }
    }

    void loadFromFile(Scanner past, String childPrefixTailPath ) {
        String delim = ",|\r\n|\n";
        String lastfile = "None";
        Pattern delimPattern = Pattern.compile(delim);
        past.useDelimiter(delimPattern);
        while (past.hasNextLine()) {
            try {
                long size = past.nextLong();
                if (size < 0) {
                    separator = past.next();
                    past.nextLine();
                } else {
                    long lastModifiedDate = past.nextLong();
                    if( past.hasNextLong() ) {
                        // 0,s,m,h
                        size = lastModifiedDate;
                        lastModifiedDate = past.nextLong();
                    }
                    String hash = past.next();
                    String checkSum = past.next();
                    String path = past.next();
                    String x = past.nextLine();
                    if (x != null && x.length() > 0)
                        path = path + x; // This is to handle the case where there is a comma in the file name.
                    lastfile = path;
                    FileRecord rec = new FileRecord(size, lastModifiedDate, hash, checkSum, null==childPrefixTailPath?path:childPrefixTailPath+File.separator+path, FileRecord.NOCHANGE);
                    add(rec);
                }
            } catch (NoSuchElementException e) {
                System.out.println("Error in reading past library, last file checksum read is "+lastfile);
            }
        }
    }

    public int extract(SearchEngine pastLibrary)
    {
        int numberOfExtracted = 0;
        for( int k=0; pastLibrary != null && k<pastLibrary.allRecords.size(); ++k )
        {
            FileRecord rec = pastLibrary.allRecords.get(k);
            FileRecord nRec = rec.setTopDirectory(pastLibrary.topDirectory, topDirectory, pastLibrary.separator);
            if( nRec != null ) {
                ++numberOfExtracted;
                add( nRec );
            }
        }
        return numberOfExtracted;
    }

    public int carryOverFrom(SearchEngine archivePastLibrary )
    {
        int numberOfCarried = 0;
        for( int k=0; k<archivePastLibrary.allRecords.size(); ++k ) {
            FileRecord rec = archivePastLibrary.allRecords.get(k);
            if( rec.tag==FileRecord.NOCHANGE ) {
                add(rec);
                numberOfCarried++;
            }
        }
        return numberOfCarried;
    }

    private boolean archiveOne( String masterTopDirectory, FileRecord mRec, SearchEngine newLibrary, PrintStream rpt, String prompt )
    {
        File m = new File( masterTopDirectory, mRec.tailPath );
        File a = new File( newLibrary.topDirectory, mRec.tailPath );

        switch (actionLevel) {
        case 0:
            (rpt==null?System.out:rpt).println("Copy " + prompt + " " + a.getAbsolutePath());
            return false;
        case 1:
            System.out.print("From " + m.getAbsolutePath() + " to "+ a.getAbsolutePath() + ", type c(opy) to copy or skip : ");
            String answer = (null==System.console()) ?
                        "h" : System.console().readLine();
            if( ! answer.startsWith("c") ) {
                (rpt==null?System.out:rpt).println(answer+": not copy " + prompt + " " + a.getAbsolutePath());
                return false;
            }
        case 2:
            try {
                File parent = a.getParentFile();
                if( ! parent.exists() )
                    parent.mkdirs();
                Files.copy(m.toPath(), a.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                (rpt==null?System.out:rpt).println("Copied " + prompt + " " + a.getAbsolutePath());
                mRec.markNew();
                newLibrary.add(mRec);
                return true;
            } catch (Exception e) {
                (rpt==null?System.out:rpt).println("Failure whiling copying to " + prompt + " " + a.getAbsolutePath());
                return false;
            }
        }
        return false;
    }

    // Archive from master directory (source, this library) to archive directory (archive library), log activities in archive solution file.
    public int archiveTo(SearchEngine archivePastLibrary, SearchEngine archiveNewLibrary, PrintStream rpt)
    {
        int numberOfArchived = 0;
        actionLevel = Integer.valueOf(System.getProperty("MyCompare.ActionLevel"));

        for( int k=0; k<allRecords.size(); ++k )
        {
            FileRecord rec = allRecords.get(k);
            FileRecord aRec = (null==archivePastLibrary?null:archivePastLibrary.findByTailPath(rec.tailPath));
            if( null==aRec) {
                // new file not in master
                if( archiveOne( topDirectory, rec, archiveNewLibrary, rpt, "new file" ) )
                    numberOfArchived++;
            } else {
                if( rec.checksum.equalsIgnoreCase(aRec.checksum) )
                    // no change
//                    archiveNewLibrary.add(aRec);
                    ;
                else if( rec.lastModifiedDate > aRec.lastModifiedDate ) {
                    // master has updated
                    if( archiveOne( topDirectory, rec, archiveNewLibrary, rpt, "updated file" ) ) {
                        numberOfArchived++;
                        aRec.markInvalid();
                    }
                } else if( rec.lastModifiedDate == aRec.lastModifiedDate ) {
                    if( archiveOne( topDirectory, rec, archiveNewLibrary, rpt, "same time updated file" ) ) {
                        numberOfArchived++;
                        aRec.markInvalid();
                    }
                } else { // rec.lastModifiedDate < aRec.lastModifiedDate
                    // archive has updated
                    rpt.println("Archived file " + aRec.tailPath + "(" + (new Date(aRec.lastModifiedDate)).toString() +
                            ") is newer (>) than master " + (new Date(rec.lastModifiedDate)).toString());
                }
            }
        }
        return numberOfArchived;
    }

    public void reportStatus(PrintStream rpt )
    {
        if( topDirectory != null ) rpt.println("Top Directory:"+topDirectory);
        for ( String name : byTailPath.keySet() ) {
            ArrayList<Integer> set = byTailPath.get(name);
            if( set.size() > 1 )
                rpt.println( "File tail path  " + name + " has " + set.size() + " files");
        }
        if( MyCompare.verbose )
            for ( Long size : bySize.keySet() ) {
                ArrayList<Integer> set = bySize.get(size);
                if( set.size() > 1 )
                    rpt.println( "File size " + size + " has " + set.size() + " files");
            }
        for ( String cksum : byCheckSum.keySet() ) {
            ArrayList<Integer> set = byCheckSum.get(cksum);
            if( set.size() > 1 ) {
                rpt.println("Checksum " + cksum + " has " + set.size() + " files");
                for( int k=0; k<set.size(); ++k )
                    rpt.println(allRecords.get(set.get(k)).toString(topDirectory+File.separator));
            }
        }
    }
}
