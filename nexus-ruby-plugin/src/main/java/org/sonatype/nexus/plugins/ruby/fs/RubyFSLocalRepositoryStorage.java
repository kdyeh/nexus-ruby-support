package org.sonatype.nexus.plugins.ruby.fs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.codehaus.plexus.util.IOUtil;
import org.sonatype.nexus.mime.MimeSupport;
import org.sonatype.nexus.plugins.ruby.RubyGroupRepository;
import org.sonatype.nexus.plugins.ruby.RubyRepository;
import org.sonatype.nexus.proxy.ItemNotFoundException;
import org.sonatype.nexus.proxy.LocalStorageException;
import org.sonatype.nexus.proxy.ResourceStoreRequest;
import org.sonatype.nexus.proxy.item.AbstractStorageItem;
import org.sonatype.nexus.proxy.item.ContentLocator;
import org.sonatype.nexus.proxy.item.DefaultStorageCollectionItem;
import org.sonatype.nexus.proxy.item.DefaultStorageFileItem;
import org.sonatype.nexus.proxy.item.FileContentLocator;
import org.sonatype.nexus.proxy.item.LinkPersister;
import org.sonatype.nexus.proxy.item.PreparedContentLocator;
import org.sonatype.nexus.proxy.item.RepositoryItemUid;
import org.sonatype.nexus.proxy.item.StorageFileItem;
import org.sonatype.nexus.proxy.item.StorageItem;
import org.sonatype.nexus.proxy.repository.Repository;
import org.sonatype.nexus.proxy.storage.UnsupportedStorageOperationException;
import org.sonatype.nexus.proxy.storage.local.fs.DefaultFSLocalRepositoryStorage;
import org.sonatype.nexus.proxy.storage.local.fs.FSPeer;
import org.sonatype.nexus.proxy.wastebasket.Wastebasket;
import org.sonatype.nexus.ruby.BundlerDependencies;
import org.sonatype.nexus.ruby.SpecsIndexType;

@Singleton
@Named( "rubyfile" )
public class RubyFSLocalRepositoryStorage extends DefaultFSLocalRepositoryStorage implements RubyLocalRepositoryStorage
{

    private static final String NEXUS_PREFIX = ".nexus";
    private static final String NEXUS_TRASH_PREFIX = RepositoryItemUid.PATH_SEPARATOR + NEXUS_PREFIX + RepositoryItemUid.PATH_SEPARATOR + "trash" + RepositoryItemUid.PATH_SEPARATOR;
    private static final String NEXUS_TEMP_PREFIX = RepositoryItemUid.PATH_SEPARATOR + NEXUS_PREFIX + RepositoryItemUid.PATH_SEPARATOR + "tmp" + RepositoryItemUid.PATH_SEPARATOR;

    @Inject
    public RubyFSLocalRepositoryStorage( Wastebasket wastebasket,
            LinkPersister linkPersister, MimeSupport mimeSupport, FSPeer fsPeer )
    {
        super( wastebasket, linkPersister, mimeSupport, fsPeer );
    }
        
    private ResourceStoreRequest fixPath(ResourceStoreRequest request)
    {
        // put the gems into subdirectory with first-letter of the gems name
        request.setRequestPath( RubygemFile.fromFilename( request.getRequestPath() ).getPath() );
        return request;
    }

    
    @Override
    public Collection<StorageItem> listItems(Repository repository,
            ResourceStoreRequest request) throws ItemNotFoundException,
            LocalStorageException {
        if ( request.getRequestPath().equals( "/" ) ) 
        {
    
            Collection<StorageItem> result = new ArrayList<StorageItem>( 8 );
                      
            result.add( newCollection( repository, "/api" ) );
            result.add( newCollection( repository, "/gems" ) );
            result.add( newCollection( repository, "/quick" ) );
            result.add( newItem( repository, "/latest_specs.4.8" ) );
            result.add( newItem( repository, "/latest_specs.4.8.gz" ) );
            result.add( newItem( repository, "/prerelease_specs.4.8" ) );
            result.add( newItem( repository, "/prerelease_specs.4.8.gz" ) );
            result.add( newItem( repository, "/specs.4.8" ) );
            result.add( newItem( repository, "/specs.4.8.gz" ) );
                    
            return result;
            
        }
        if ( ! request.getRequestPath().startsWith( "/" + NEXUS_PREFIX ) )
        {
            Collection<StorageItem> result = super.listItems( repository, request );
            for( StorageItem file: result )
            {
                String url = file.getRemoteUrl();
                if ( url != null && url.endsWith( ".gem" ) )
                { 
                    AbstractStorageItem item = (AbstractStorageItem) file;
                    // FIXME is the pattern right ? what does this do anyways ?
                    item.getResourceStoreRequest().setRequestUrl( url.replaceFirst( "/[a-z]/", "/" ) );
                    item.getResourceStoreRequest().setRequestPath( item.getResourceStoreRequest().getRequestPath().replaceFirst( "/[a-z]/", "/" ) );
                    item.setPath(item.getPath().replaceFirst( "/[a-z]/", "/" ) );
                    item.setRemoteUrl(url.replaceFirst( "/[a-z]/", "/" ));
                }
            }
            return result;
        }
        return super.listItems( repository, request );
    }

    private StorageItem newItem( Repository repository, String name )
    {
        return new DefaultStorageFileItem( repository, new ResourceStoreRequest( name ), true, false, new ContentLocator() {
            
            @Override
            public boolean isReusable()
            {
                return false;
            }
            
            @Override
            public String getMimeType()
            {
                return null;
            }
            
            @Override
            public long getLength()
            {
                return 0;
            }
            
            @Override
            public InputStream getContent() throws IOException
            {
                return null;
            }
        } );
    }

    private StorageItem newCollection( Repository repository, String name )
    {
        return new DefaultStorageCollectionItem( repository, new ResourceStoreRequest( name ), true, false );
    }

    @Override
    public boolean containsItem(Repository repository,
            ResourceStoreRequest request) throws LocalStorageException
    {
        return super.containsItem( repository, fixPath( request ) );
    }

    @Override
    public AbstractStorageItem retrieveItem( Repository repository,
            ResourceStoreRequest request ) throws ItemNotFoundException,
            LocalStorageException 
    {
        SpecsIndexType type = SpecsIndexType.fromFilename( request.getRequestPath() );
        if ( type != null )
        {
            if ( request.getRequestPath().startsWith( "/.nexus/" ) || request.getRequestPath().endsWith( ".gz" ) )
            {
                // do not fix the path !!
                return super.retrieveItem( repository, request );
            }
            else
            {
                return (AbstractStorageItem) retrieveSpecsIndex( (RubyRepository) repository, type );                     
            }
        }
        // fix the path !!
        return super.retrieveItem( repository, fixPath( request ) );            
    }
    
    @Override
    public void superStoreItem(  Repository repository, StorageItem item ) 
            throws LocalStorageException, UnsupportedStorageOperationException{
        super.storeItem( repository, item );
    }
    
    @Override
    public void storeItem( Repository repository, StorageItem item )
            throws UnsupportedStorageOperationException, LocalStorageException
    {
        if ( "/api/v1/api_key".equals( item.getPath() ) )
        {
            throw new RuntimeException( "not implemented !");
        }
        if ( "/api/v1/gems".equals( item.getPath() ) )
        {            
            File tmpDir = getFileFromBase( repository, new ResourceStoreRequest( NEXUS_TEMP_PREFIX ) );
            File tmpFile = null;
            try
            {
                tmpDir.mkdirs();
                tmpFile = File.createTempFile( "gems-", ".gem", tmpDir );
                IOUtil.copy( ((StorageFileItem)item).getInputStream(), new FileOutputStream( tmpFile ) );

                FileContentLocator locator = new FileContentLocator( tmpFile, "application/octect" );
                ((StorageFileItem)item).setContentLocator( locator );
                RubygemFile file = ( (RubyRepository) repository ).getRubygemsFacade().addGem( this, (StorageFileItem) item );
                super.moveItem( repository, 
                                new ResourceStoreRequest( new ResourceStoreRequest( NEXUS_TEMP_PREFIX +
                                                                                    RepositoryItemUid.PATH_SEPARATOR +
                                                                                    tmpFile.getName() ) ), 
                                new ResourceStoreRequest( file.getPath() ) );
                
                item.getResourceStoreRequest().setRequestPath( file.getPath() );
                ((AbstractStorageItem) item).setPath( file.getPath() );
            } 
            catch ( IOException e )
            {
                throw new LocalStorageException( "error creating temp gem file", e );
            }
            catch (ItemNotFoundException e)
            {
                throw new LocalStorageException( "error moving gem file into right place", e );
            }
            finally
            {
                if ( tmpFile != null )
                {
                    tmpFile.delete();
                }
            }
            return;
        }

        RubygemFile file = RubygemFile.fromFilename( item.getPath() );
        switch( file.getType() ){
        case GEM:
            
            ((AbstractStorageItem) item).setPath( file.getPath() );
            item.getResourceStoreRequest().setRequestPath( item.getPath() );
            super.storeItem( repository, item );

            if ( !item.getPath().startsWith("/.nexus" ) )
            {
                // add it to the rubygems-index files
                File gem = getFileFromBase( repository, item.getResourceStoreRequest() );
                FileContentLocator locator = new FileContentLocator( gem, file.getMime() );
                ((StorageFileItem) item).setContentLocator( locator );
                
                try
                {
                    ( (RubyRepository) repository ).getRubygemsFacade().addGem( this, (StorageFileItem) item );
                }
                catch( RuntimeException e )
                {
                    try 
                    {
                        super.shredItem( repository, item.getResourceStoreRequest() );
                    } 
                    catch (ItemNotFoundException ee ) {
                        // ignored
                    }
                    throw e;
                }
            }
            
            return;
        case GEMSPEC:
            fixPath( item.getResourceStoreRequest() );
        default:
            // nothing to do
        }
        super.storeItem( repository, item );
    }
    
    @Override
    public void moveItem(Repository repository, ResourceStoreRequest from,
            ResourceStoreRequest to) throws ItemNotFoundException,
            UnsupportedStorageOperationException, LocalStorageException
    {
        if ( to.getRequestPath().startsWith( NEXUS_TRASH_PREFIX ) && ! from.getRequestPath().contains( NEXUS_PREFIX ) )
        {
            RubygemFile file = ( (RubyRepository) repository ).getRubygemsFacade().deletableFile( from.getRequestPath() );
            if ( file != null )
            {
                
                StorageFileItem item = (StorageFileItem) retrieveItem( repository, from );
                boolean deleted;
                try
                {
                    
                    // remove the gem from the index files
                    deleted = ( (RubyRepository) repository ).getRubygemsFacade().removeGem( this, item );
                }
                catch (IOException e) {
                    throw new LocalStorageException( "gem-file can not be found.", e );
                }
                if ( deleted )
                {
                    try
                    {
                        // delete gemspec as well
                        super.shredItem( repository, new ResourceStoreRequest( file.getGemspecRz() ) );
                    }
                    catch( ItemNotFoundException e )
                    {
                        // ignored
                    }
                }
            }
            else if ( file == null && ! from.getRequestPath().contains( NEXUS_PREFIX ) )
            {
                throw new UnsupportedStorageOperationException( "only gem files can be deleted: " + from.getRequestPath() );
            }
        }
        else if ( ! from.getRequestPath().contains( NEXUS_PREFIX ) )
        {
            throw new UnsupportedStorageOperationException( "filenames with gems are part of the data itself." );
        }
        
        super.moveItem( repository, from, to );
    }

    // RubyLocalRepositoryStorage

    @Override
    public StorageFileItem retrieveSpecsIndex(RubyRepository repository,
            SpecsIndexType type) throws LocalStorageException, ItemNotFoundException
	{

        // some old repos used the unzipped specs.4.8 files
        // create a zipped version of it as the current implementation needs it
        ResourceStoreRequest req = new ResourceStoreRequest( type.filepath() );
        ResourceStoreRequest request = new ResourceStoreRequest( type.filepathGzipped() );
        if ( !containsItem( repository, request) && containsItem( repository, req ) ){
            StorageFileItem item = (StorageFileItem) super.retrieveItem( repository,  req );
            
            ContentLocator content = new GzipContentGenerator().generateContent( repository, null, item );
            DefaultStorageFileItem zippedItem = new DefaultStorageFileItem( repository, 
                    new ResourceStoreRequest( type.filepathGzipped(), true, false ), 
                    true, false, content );
            try
            {
                storeItem( repository, zippedItem );
		// keep unzipped file in place since deleting it ends up in 
		// infinite recursion
                //deleteItem( repository, req );
            }
            catch (UnsupportedStorageOperationException e)
            {
                throw new RuntimeException( "BUG : should be able to write on repository" );
            }
        }
        StorageFileItem item = (StorageFileItem) super.retrieveItem( repository,  request );
        DefaultStorageFileItem unzippedItem = new DefaultStorageFileItem( repository, 
                new ResourceStoreRequest( type.filepath(), true, false ), 
                true, false,
                item.getContentLocator() );
        unzippedItem.setContentGeneratorId( GunzipContentGenerator.ID );
        unzippedItem.setModified( item.getModified() );
        return unzippedItem;
	}

    @Override
    public void storeSpecsIndex(RubyRepository repository, SpecsIndexType type,
            InputStream content) throws LocalStorageException, UnsupportedStorageOperationException {
        OutputStream out = null;
        try
        {
            ByteArrayOutputStream gzipped = new ByteArrayOutputStream();
            out = new GZIPOutputStream( gzipped );
            IOUtil.copy( content, out );
            out.close();
            DefaultStorageFileItem item = new DefaultStorageFileItem( repository, new ResourceStoreRequest( type.filename() + ".gz" ), 
                    true, true,
                    new PreparedContentLocator( new ByteArrayInputStream( gzipped.toByteArray() ),
                                                "application/x-gzip",
                                                gzipped.size() ) );
            storeItem( repository,  item );
        }
        catch (IOException e)
        {
            new LocalStorageException( "error storing: " + type.filename(), e);
        }
        finally
        {
            IOUtil.close( content );
            IOUtil.close( out );
        }
    }

    public void storeSpecsIndices( RubyGroupRepository rubyRepository, SpecsIndexType type, List<StorageItem> specsItems)
            throws UnsupportedStorageOperationException, LocalStorageException
    {
        StorageFileItem localSpecsItem = null;
        try
        {
            localSpecsItem = retrieveSpecsIndex( rubyRepository, type );
        }
        catch ( ItemNotFoundException e )
        {
            // Ignored. there are situations like after creating such a repo
        }
        
        boolean outdated = true; // outdate is true if there are no local-specs 
        if ( localSpecsItem != null )
        {
            // using the timestamp from the file since localSpecsItem.getModified() produces something but
            // not from .nexus/attributes/* file !!!
            long modified = ((FileContentLocator) localSpecsItem.getContentLocator()).getFile().lastModified();
            outdated = false;
            for ( StorageItem item: specsItems )
            {     
                outdated = outdated || ( item.getModified() > modified );
            }
        }

        if ( outdated && !specsItems.isEmpty() )
        {
            try
            {
            
                rubyRepository.getRubygemsFacade().mergeSpecsIndex( this, type, localSpecsItem, specsItems );
 
            }
            catch ( IOException e )
            {
                throw new LocalStorageException( e );
            }
        }
	}

    @Override
    public StorageFileItem createBundlerTempStorageFile( RubyRepository repository,
            BundlerDependencies bundler) 
                    throws LocalStorageException
    {
        return createTempStorageFile( repository, bundler.dump(), null );
    }
    
    @Override
    public StorageFileItem createTempStorageFile( RubyRepository repository,
                                                  InputStream in,
                                                  String mime ) 
                    throws LocalStorageException
    {
        File tmpDir = getFileFromBase( repository, new ResourceStoreRequest( NEXUS_TEMP_PREFIX ) );
        File tmpFile;
        try
        {
            tmpDir.mkdirs();
            tmpFile = File.createTempFile( "bundler-", ".json", tmpDir );
            IOUtil.copy( in, new FileOutputStream( tmpFile ) );
        } 
        catch (IOException e) {
            throw new LocalStorageException( "error creating temp file", e );
        }
        if (mime == null ){
            mime =  getMimeSupport().guessMimeTypeFromPath( repository.getMimeRulesSource(),
                                                            tmpFile.getAbsolutePath() );
        }
        ResourceStoreRequest request = new ResourceStoreRequest( NEXUS_TEMP_PREFIX + tmpFile.getName(), 
                true, false );
        DefaultStorageFileItem file =
                new DefaultStorageFileItem( repository, 
                                            request, 
                                            tmpFile.canRead(),
                                            tmpFile.canWrite(),
                                            new FileContentLocator( tmpFile,
                                                                    mime,
                                                 // set delete after close to true
                                                                    true ) );
        try
        {
            repository.getAttributesHandler().fetchAttributes( file );
        
            file.setModified( tmpFile.lastModified() );
            file.setCreated( tmpFile.lastModified() );

            repository.getAttributesHandler().touchItemLastRequested( System.currentTimeMillis(), file );
            
        }
        catch ( IOException e )
        {
            throw new LocalStorageException( "Exception during reading up an item from FS storage!", e );
        }
        finally {
            IOUtil.close( in );
        }
        return file;
    }
}
