import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
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

    void loadFromFile(Scanner past) {
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
                    FileRecord rec = new FileRecord(size, lastModifiedDate, hash, checkSum, path, 0);
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

    public int findUpdate(SearchEngine masterLibrary, PrintStream rpt)
    {
        int numberOfExtracted = 0;
        for( int k=0; k<allRecords.size(); ++k )
        {
            FileRecord rec = allRecords.get(k);
            FileRecord nRec = masterLibrary.findByTailPath(rec.tailPath);
            if( null==nRec) {
                // it's been deleted from master directory
                rpt.println( "rem master version has deleted "+rec.tailPath);
            } else {
                if( rec.checksum.equalsIgnoreCase(nRec.checksum) )
                    // no change
                    ;
                else if( rec.lastModifiedDate < nRec.lastModifiedDate ) {
                    // master has updated
                    rpt.println("rem master has updated: " + rec.tailPath);
                    rpt.println("copy \"" + masterLibrary.topDirectory + masterLibrary.separator + nRec.tailPath + "\" \"" + topDirectory + separator + rec.tailPath+"\"");
                } else if( rec.lastModifiedDate == nRec.lastModifiedDate ) {
                    // not modified
                    rpt.println("rem checksumm differs but same time stamp: " + rec.tailPath);
                    rpt.println("copy \"" + masterLibrary.topDirectory + masterLibrary.separator + nRec.tailPath + "\" \"" + topDirectory + separator + rec.tailPath+"\"");
                } else {
                    // archive has updated
//                    rpt.println("rem archive has updated: " + rec.tailPath);
                    rpt.println("rem checksumm differs but master version is earlier " + rec.tailPath);
                }
            }
        }
        return numberOfExtracted;
    }

    public void reportStatus(PrintStream rpt )
    {
        if( topDirectory != null ) rpt.println("Top Directory:"+topDirectory);
        for ( String name : byTailPath.keySet() ) {
            ArrayList<Integer> set = byTailPath.get(name);
            if( set.size() > 1 )
                rpt.println( "File tail path  " + name + " has " + set.size() + " files");
        }
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
                    rpt.println(allRecords.get(set.get(k)).toString(topDirectory));
            }
        }
    }
}
