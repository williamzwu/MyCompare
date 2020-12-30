import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.util.Scanner;
import java.util.Vector;
import java.util.regex.Pattern;

public class FileFilter implements FilenameFilter {
    private Vector<Pattern> filenameIgnored;
    private Vector<Pattern> directoryIgnored;
    public boolean accept(File dir,
                   String name)
    {
        for( int k=0; k<filenameIgnored.size(); ++k )
            if( filenameIgnored.elementAt(k).matcher(name).find() )  {
                File x = new File( dir, name );
                if( x.isFile() ) return false;
            }
        for( int k=0; k<directoryIgnored.size(); ++k )
            if( directoryIgnored.elementAt(k).matcher(name).find() ) {
                File x = new File( dir, name );
                if( x.isDirectory() ) return false;
            }
        return true;
    }

    public FileFilter( String filterName )
    {
        filenameIgnored = new Vector<Pattern>();
        directoryIgnored = new Vector<Pattern>();
        File filterFile = new File( filterName );
        try {
            Scanner myReader = new Scanner(filterFile);
            while (myReader.hasNextLine()) {
                String nocomment[] = myReader.nextLine().split("##");
                String data = nocomment[0];
                if( data.startsWith("f:")) {
                    String[] part = data.split(":");
                    if( part.length>1 ) {
                        String wildcard = part[1].trim().replaceAll("\\*", "\\.\\*");
                        filenameIgnored.add(Pattern.compile(wildcard.trim()));
                    }
                }
                if( data.startsWith("d:")) {
                    String[] part = data.split(":");
                    if( part.length>1 ) {
                        String wildcard = part[1].trim().replaceAll("\\*", "\\.\\*");
                        directoryIgnored.add(Pattern.compile(wildcard.trim()));
                    }
                }
            }
            myReader.close();
        } catch ( FileNotFoundException e) {
            System.out.println( filterName + ": error occurred.");
            e.printStackTrace();
        }

    }
}
