public class FileRecord {
    public long size;
    public long lastModifiedDate;
    public String hash;
    public String checksum;
//    public String fullPath;
    public String tailPath;
    public int tag;
    final public static int NOCHANGE = 0;
    final public static int FIRSTTIME = 1;
    final public static int INVALID = -1;

//    private boolean isValid;

    public FileRecord(long _size, long _lastModifiedDate, String _hash, String _checkSum, String _tailPath, int _tag )
    {
        size = _size;
        lastModifiedDate = _lastModifiedDate;
        hash = _hash;
        checksum = _checkSum;
        tailPath = _tailPath;
        tag = _tag;
    }

    public FileRecord( String csv )
    {
        String[] v = csv.split(",");
        size = Long.getLong(v[0]);
        lastModifiedDate = Long.getLong(v[1]);
        hash = v[2];
        checksum = v[3];
        tailPath = v[4];
  //      isValid = true;
    }

    private void mark( int tag )
    {
        this.tag = tag;
    }

    public void markInvalid()
    {
        mark(INVALID);
    }

    public void markNew()
    {
        mark(FIRSTTIME);
    }

    public String fullPath( String top, String separator )
    {
        return top + separator + tailPath;
    }

    public FileRecord setTopDirectory( String oldTop, String newTop, String separator )
    {
        String f = fullPath( oldTop, separator );
        if( f.startsWith(newTop+separator) )
            return new FileRecord(size, lastModifiedDate, hash, checksum, f.substring(newTop.length()+separator.length()), 0);
        else
            return null;
    }

    public static String getTailPath( String fullPath, String top, String separator )
    {
        if( fullPath.startsWith(top+separator) )
            return fullPath.substring(top.length()+separator.length());
        else
            return null;
    }

    public String toString(String topDirectorySlash) {
        String r = tag + ","
                + size + ","
                + lastModifiedDate + ","
                + hash + ","
                + checksum + ","
                + (null==topDirectorySlash ? tailPath : topDirectorySlash+tailPath);
        return r;
    }

    public byte[] toBytes(String topDirectorySlash) {
        String r = tag + ","
                + size + ","
                + lastModifiedDate + ","
                + hash + ","
                + checksum + ","
                + (null==topDirectorySlash ? tailPath : topDirectorySlash+tailPath);
        return r.getBytes();
    }
}
