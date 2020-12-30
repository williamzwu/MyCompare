import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;

public enum Hash {

    MD5("MD5"),
    SHA1("SHA1"),
    SHA256("SHA-256"),
    SHA512("SHA-512");

    public static Hash getByName( String name )
    {
        if( name.equalsIgnoreCase("SHA-256") ) return SHA256;
        if( name.equalsIgnoreCase("SHA-512") ) return SHA512;
        if( name.equalsIgnoreCase("SHA1") ) return SHA1;
        if( name.equalsIgnoreCase("MD5") ) return MD5;
        return SHA256;
    }

    private String name;

    Hash(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public byte[] checksum(File input)
        throws Exception
    {
        try (InputStream in = new FileInputStream(input)) {
            MessageDigest digest = MessageDigest.getInstance(getName());
            byte[] block = new byte[4096];
            int length;
            while ((length = in.read(block)) > 0) {
                digest.update(block, 0, length);
            }
            return digest.digest();
        }
        /*
        catch (Exception e) {
            e.printStackTrace();
        }
         */
    }

}