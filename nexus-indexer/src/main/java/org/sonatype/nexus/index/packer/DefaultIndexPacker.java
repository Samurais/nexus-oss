/**
 * Copyright (c) 2007-2008 Sonatype, Inc. All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License Version 1.0, which accompanies this distribution and is
 * available at http://www.eclipse.org/legal/epl-v10.html.
 */
package org.sonatype.nexus.index.packer;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.LockObtainFailedException;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;
import org.sonatype.nexus.index.ArtifactInfo;
import org.sonatype.nexus.index.context.IndexCreator;
import org.sonatype.nexus.index.context.IndexUtils;
import org.sonatype.nexus.index.context.IndexingContext;
import org.sonatype.nexus.index.context.NexusLegacyAnalyzer;
import org.sonatype.nexus.index.creator.LegacyDocumentUpdater;
import org.sonatype.nexus.index.packer.IndexPackingRequest.IndexFormat;
import org.sonatype.nexus.index.updater.IndexDataWriter;

/**
 * A default {@link IndexPacker} implementation. Creates the properties, legacy index zip and new gz files.
 * 
 * @author Tamas Cservenak
 * @author Eugene Kuleshov
 */
@Component( role = IndexPacker.class )
public class DefaultIndexPacker
    extends AbstractLogEnabled
    implements IndexPacker
{
    public void packIndex( IndexPackingRequest request )
        throws IOException,
            IllegalArgumentException
    {
        if ( request.getTargetDir() == null )
        {
            throw new IllegalArgumentException( "The target dir is null" );
        }

        if ( request.getTargetDir().exists() )
        {
            if ( !request.getTargetDir().isDirectory() )
            {
                throw new IllegalArgumentException( //
                    String.format( "Specified target path %s is not a directory", request
                        .getTargetDir().getAbsolutePath() ) );
            }
            if ( !request.getTargetDir().canWrite() )
            {
                throw new IllegalArgumentException( String.format( "Specified target path %s is not writtable", request
                    .getTargetDir().getAbsolutePath() ) );
            }
        }
        else
        {
            if ( !request.getTargetDir().mkdirs() )
            {
                throw new IllegalArgumentException( "Can't create " + request.getTargetDir().getAbsolutePath() );
            }
        }
        
        // These are all of the files we'll be dealing with (except for the incremental chunks of course)
        File propertiesFile = new File( request.getTargetDir(), IndexingContext.INDEX_FILE + ".properties" );
        File legacyFile = new File( request.getTargetDir(), IndexingContext.INDEX_FILE + ".zip" );
        File v1File = new File( request.getTargetDir(), IndexingContext.INDEX_FILE + ".gz" );
        
        // In case there is no main index we will force it to be created
        boolean forceMainIndexGeneration = false;

        Properties info = null;
        
        try
        {
            // Note that for incremental indexes to work properly, a valid index.properties file
            // must be present
            info = readIndexProperties( propertiesFile );
            
            if ( request.isCreateIncrementalChunks() )
            {
                // Get the list of document ids that have been added since the last time
                // the index ran
                List<Integer> chunk = getIndexChunk( request, info );

                // if chunk is null, means we had problems with property file, generate whole index instead
                if ( chunk != null )
                {
                    // if no documents, then we dont need to do anything, no changes
                    if ( chunk.size() > 0 )
                    {
                        writeIndexChunk( info, chunk, request );
                    }
                }
                else
                {
                    forceMainIndexGeneration = true;
                }
            }
        }
        catch ( IOException e )
        {
            getLogger().info( "Unable to read properties file, will force index regeneration" );
            info = new Properties();
            // new properties, so initialize to 0 for index chunks
            info.setProperty( IndexingContext.INDEX_CHUNK_COUNTER, "0" );
            forceMainIndexGeneration = true;
        }
        
        // When there are no main indexes available, and none requested, we need to write them out
        if ( request.getFormats().isEmpty() 
            && !legacyFile.exists()
            && !v1File.exists() )
        {
            forceMainIndexGeneration = true;
        }
        
        if ( forceMainIndexGeneration )
        {
            getLogger().debug( "Forcing add of LEGACY and V1 formats to request" );
            request.setFormats( Arrays.asList( IndexFormat.FORMAT_LEGACY, IndexFormat.FORMAT_V1 ) );
        }

        if ( request.getFormats().contains( IndexPackingRequest.IndexFormat.FORMAT_LEGACY ) )
        {
            writeIndexArchive( request.getContext(), legacyFile );

            if ( request.isCreateChecksumFiles() )
            {
                FileUtils.fileWrite(
                    new File( legacyFile.getParentFile(), legacyFile.getName() + ".sha1" ).getAbsolutePath(),
                    DigesterUtils.getSha1Digest( legacyFile ) );

                FileUtils.fileWrite(
                    new File( legacyFile.getParentFile(), legacyFile.getName() + ".md5" ).getAbsolutePath(),
                    DigesterUtils.getMd5Digest( legacyFile ) );
            }
        }

        if ( request.getFormats().contains( IndexPackingRequest.IndexFormat.FORMAT_V1 ) )
        {
            writeIndexData( request.getContext(), null, v1File );

            if ( request.isCreateChecksumFiles() )
            {
                FileUtils.fileWrite(
                    new File( v1File.getParentFile(), v1File.getName() + ".sha1" ).getAbsolutePath(),
                    DigesterUtils.getSha1Digest( v1File ) );

                FileUtils.fileWrite(
                    new File( v1File.getParentFile(), v1File.getName() + ".md5" ).getAbsolutePath(),
                    DigesterUtils.getMd5Digest( v1File ) );
            }
        }

        writeIndexProperties( request, info, propertiesFile );

        if ( request.isCreateChecksumFiles() )
        {
            FileUtils.fileWrite(
                new File( propertiesFile.getParentFile(), propertiesFile.getName() + ".sha1" ).getAbsolutePath(),
                DigesterUtils.getSha1Digest( propertiesFile ) );

            FileUtils.fileWrite(
                new File( propertiesFile.getParentFile(), propertiesFile.getName() + ".md5" ).getAbsolutePath(),
                DigesterUtils.getMd5Digest( propertiesFile ) );
        }
    }

    private List<Integer> getIndexChunk( IndexPackingRequest request, Properties properties )
        throws IOException
    {        
        Date timestamp = null;
        
        try
        {
            timestamp = parse( ( String ) properties.get( IndexingContext.INDEX_TIMESTAMP ) );
        }
        catch ( ParseException e )
        {
            getLogger().debug( "Invalid timestamp, no incremental index being created." );
            return null;
        }
        
        List<Integer> chunk = new ArrayList<Integer>();

        IndexReader r = request.getContext().getIndexReader();

        for ( int i = 0; i < r.numDocs(); i++ )
        {
            if ( !r.isDeleted( i ) )
            {
                Document d = r.document( i );

                String lastModified = d.get( ArtifactInfo.LAST_MODIFIED );

                if ( lastModified != null )
                {
                    Date t = new Date( Long.parseLong( lastModified ) );
                    
                    // Only add documents that were added after the last time we indexed
                    if ( t.after( timestamp ) )
                    {
                        chunk.add( i );
                    }
                }
            }
        }

        return chunk;
    }
    
    private Properties readIndexProperties( File propertyFile )
        throws IOException
    {
        Properties properties = new Properties();
        
        FileInputStream fos = null;
        
        try
        {            
            fos = new FileInputStream( propertyFile );
            properties.load( fos );
        }
        finally
        {
            if ( fos != null )
            {
                fos.close();
            }
        }
        
        return properties;
    }

    void writeIndexChunk( Properties info, List<Integer> chunk, IndexPackingRequest request )
        throws IOException
    {
        IndexingContext context = request.getContext();
        
        updatePropertyFileUpdateKeys( info, request );
        
        String val = ( String ) info.getProperty( IndexingContext.INDEX_CHUNK_COUNTER );
        
        if ( StringUtils.isEmpty( val ) )
        {
            val = "0";
        }
        
        int nextValue = Integer.parseInt( val ) + 1;
        
        info.put( IndexingContext.INDEX_CHUNK_PREFIX + "0", Integer.toString( nextValue ) );
        info.put( IndexingContext.INDEX_CHUNK_COUNTER, Integer.toString( nextValue ) );
        
        File file = new File( request.getTargetDir(), //
            IndexingContext.INDEX_FILE + "." + nextValue + ".gz" );
        
        writeIndexData( context, //
            chunk,
            file );
        
        if ( request.isCreateChecksumFiles() )
        {
            FileUtils.fileWrite(
                new File( file.getParentFile(), file.getName() + ".sha1" ).getAbsolutePath(),
                DigesterUtils.getSha1Digest( file ) );

            FileUtils.fileWrite(
                new File( file.getParentFile(), file.getName() + ".md5" ).getAbsolutePath(),
                DigesterUtils.getMd5Digest( file ) );
        }
    }
    
    private void updatePropertyFileUpdateKeys( Properties properties, IndexPackingRequest request )
    {        
        Set<Object> keys = new HashSet<Object>( properties.keySet() );
        Map<Integer,String> dataMap = new TreeMap<Integer, String>(); 
        
        // First go through and retrieve all keys and their values
        for ( Object key : keys )
        {
            String sKey = ( String ) key;
            
            if ( sKey.startsWith( IndexingContext.INDEX_CHUNK_PREFIX ) )
            {                
                Integer count = Integer.valueOf( sKey.substring( IndexingContext.INDEX_CHUNK_PREFIX.length() ) );
                String value = properties.getProperty( sKey );
                
                dataMap.put( count, value );                
                properties.remove( key );
            }
        }
        
        int i = 0;
        //Next put the items back in w/ proper keys
        for ( Integer key : dataMap.keySet() )
        {
            //make sure to end if we reach limit, 0 based
            if ( i >= ( request.getMaxIndexChunks() - 1 ) )
            {
                break;
            }
              
            properties.put( IndexingContext.INDEX_CHUNK_PREFIX + ( key + 1 ), dataMap.get( key ) );
            
            i++;
        }
    }

    void writeIndexArchive( IndexingContext context, File targetArchive )
        throws IOException
    {
        if ( targetArchive.exists() )
        {
            targetArchive.delete();
        }

        OutputStream os = null;

        try
        {
            os = new BufferedOutputStream( new FileOutputStream( targetArchive ), 4096 );

            packIndexArchive( context, os );
        }
        finally
        {
            IOUtil.close( os );
        }
    }

    /**
     * Pack legacy index archive into a specified output stream
     */
    public static void packIndexArchive( IndexingContext context, OutputStream os )
        throws IOException
    {
        File indexArchive = File.createTempFile( "nexus-index", "" );

        File indexDir = new File( indexArchive.getAbsoluteFile().getParentFile(), indexArchive.getName() + ".dir" );

        indexDir.mkdirs();

        FSDirectory fdir = FSDirectory.getDirectory( indexDir );

        try
        {
            // force the timestamp update
            IndexUtils.updateTimestamp( context.getIndexDirectory(), context.getTimestamp() );
            IndexUtils.updateTimestamp( fdir, context.getTimestamp() );

            copyLegacyDocuments( context.getIndexReader(), fdir, context );
            packDirectory( fdir, os );
        }
        finally
        {
            IndexUtils.close( fdir );
            indexArchive.delete();
            IndexUtils.delete( indexDir );
        }
    }

    static void copyLegacyDocuments( IndexReader r, Directory targetdir, IndexingContext context )
        throws CorruptIndexException,
            LockObtainFailedException,
            IOException
    {
        IndexWriter w = null;
        try
        {
            w = new IndexWriter( targetdir, false, new NexusLegacyAnalyzer(), true );

            for ( int i = 0; i < r.maxDoc(); i++ )
            {
                if ( !r.isDeleted( i ) )
                {
                    w.addDocument( updateLegacyDocument( r.document( i ), context ) );
                }
            }

            w.optimize();
            w.flush();
        }
        finally
        {
            IndexUtils.close( w );
        }
    }

    static Document updateLegacyDocument( Document doc, IndexingContext context )
    {
        ArtifactInfo ai = IndexUtils.constructArtifactInfo( doc, context );
        if ( ai == null )
        {
            return doc;
        }

        Document document = new Document();
        document.add( new Field( ArtifactInfo.UINFO, ai.getUinfo(), Field.Store.YES, Field.Index.UN_TOKENIZED ) );

        for ( IndexCreator ic : context.getIndexCreators() )
        {
            if ( ic instanceof LegacyDocumentUpdater )
            {
                ( (LegacyDocumentUpdater) ic ).updateLegacyDocument( ai, document );
            }
        }

        return document;
    }

    static void packDirectory( Directory directory, OutputStream os )
        throws IOException
    {
        ZipOutputStream zos = null;
        try
        {
            zos = new ZipOutputStream( os );
            zos.setLevel( 9 );

            String[] names = directory.list();

            boolean savedTimestamp = false;

            byte[] buf = new byte[8192];

            for ( int i = 0; i < names.length; i++ )
            {
                String name = names[i];

                writeFile( name, zos, directory, buf );

                if ( name.equals( IndexUtils.TIMESTAMP_FILE ) )
                {
                    savedTimestamp = true;
                }
            }

            // FSDirectory filter out the foreign files
            if ( !savedTimestamp && directory.fileExists( IndexUtils.TIMESTAMP_FILE ) )
            {
                writeFile( IndexUtils.TIMESTAMP_FILE, zos, directory, buf );
            }
        }
        finally
        {
            IndexUtils.close( zos );
        }
    }

    static void writeFile( String name, ZipOutputStream zos, Directory directory, byte[] buf )
        throws IOException
    {
        ZipEntry e = new ZipEntry( name );

        zos.putNextEntry( e );

        IndexInput in = directory.openInput( name );

        try
        {
            int toRead = 0;

            int bytesLeft = (int) in.length();

            while ( bytesLeft > 0 )
            {
                toRead = ( bytesLeft >= buf.length ) ? buf.length : bytesLeft;
                bytesLeft -= toRead;

                in.readBytes( buf, 0, toRead, false );

                zos.write( buf, 0, toRead );
            }
        }
        finally
        {
            IndexUtils.close( in );
        }

        zos.flush();

        zos.closeEntry();
    }

    void writeIndexData( IndexingContext context, List<Integer> docIndexes, File targetArchive )
        throws IOException
    {
        if ( targetArchive.exists() )
        {
            targetArchive.delete();
        }

        OutputStream os = null;

        try
        {
            os = new FileOutputStream( targetArchive );

            IndexDataWriter dw = new IndexDataWriter( os );
            dw.write( context, docIndexes );

            os.flush();
        }
        finally
        {
            IOUtil.close( os );
        }
    }

    void writeIndexProperties( IndexPackingRequest request, Properties info, File propertiesFile )
        throws IOException
    {
        info.setProperty( IndexingContext.INDEX_ID, request.getContext().getId() );

        Date timestamp = request.getContext().getTimestamp();

        if ( timestamp == null )
        {
            timestamp = new Date( 0 ); // never updated
        }

        info.setProperty( IndexingContext.INDEX_TIMESTAMP, format( timestamp ) );

        OutputStream os = null;

        try
        {
            os = new FileOutputStream( propertiesFile );

            info.store( os, null );
        }
        finally
        {
            IOUtil.close( os );
        }
    }

    private String format( Date d )
    {
        SimpleDateFormat df = new SimpleDateFormat( IndexingContext.INDEX_TIME_FORMAT );
        df.setTimeZone( TimeZone.getTimeZone( "GMT" ) );
        return df.format( d );
    }
    
    private String formatForFilename( Date d )
    {
        SimpleDateFormat df = new SimpleDateFormat( "yyyyMMddHHmmss" );
        df.setTimeZone( TimeZone.getTimeZone( "GMT" ) );
        return df.format( d );
    }
    
    private Date parse( String s ) 
        throws ParseException
    {
        SimpleDateFormat df = new SimpleDateFormat( IndexingContext.INDEX_TIME_FORMAT );
        df.setTimeZone( TimeZone.getTimeZone( "GMT" ) );
        return df.parse( s );
    }
}
