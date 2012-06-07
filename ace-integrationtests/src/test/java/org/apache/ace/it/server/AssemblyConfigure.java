/*
 * Copyright 2009 Toni Menzel.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ace.it.server;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.ops4j.pax.exam.Option;

import static org.ops4j.pax.exam.CoreOptions.*;

/**
 * @author Toni Menzel
 * @since Dec 16, 2009
 */
public class AssemblyConfigure
{

    public static Option[] get( String name, String urlString )
    {
        try
        {
            URL url = new URL( urlString );
            File target = new File( "target/ace-assembly" );
            deleteDir( target );
            target.mkdirs();
            // unpack
            unpack( url.openStream(), target );
            // set options:
            return options(
                scanDir( target.getAbsolutePath() + "/" + name + "/ace-bundles" ),
                scanDir( target.getAbsolutePath() + "/" + name + "/required-bundles" ),
                workingDirectory( target.getAbsolutePath() + "/" + name ),
                autoWrap()
            );
        } catch( IOException e )
        {
            throw new RuntimeException( e );
        }
    }

    private static void unpack( InputStream source, File destination )
        throws IOException
    {
        ZipInputStream zin = new ZipInputStream( source );
        ZipEntry zipEntry = null;
        FileOutputStream fout = null;

        byte[] buffer = new byte[4096];
        while( ( zipEntry = zin.getNextEntry() ) != null )
        {

            System.out.println( "unpacking " + zipEntry.getName() );
            long ts = zipEntry.getTime();

            File f = new File( destination, zipEntry.getName() );

            f.getParentFile().mkdirs();
            if( zipEntry.isDirectory() )
            {
                f.mkdirs();
            }
            else
            {
                fout = new FileOutputStream( f );
                int len;
                while( ( len = zin.read( buffer ) ) > 0 )
                {
                    fout.write( buffer, 0, len );
                }
                fout.close();

            }
            zin.closeEntry();
            f.setLastModified( ts );
        }
        if( fout != null )
        {
            fout.close();
        }
    }

    private static boolean deleteDir( File dir )
    {
        if( dir.isDirectory() )
        {
            String[] children = dir.list();
            for( int i = 0; i < children.length; i++ )
            {
                boolean success = deleteDir( new File( dir, children[ i ] ) );
                if( !success )
                {
                    return false;
                }
            }
        }

        // The directory is now empty so delete it
        return dir.delete();
    }
}
