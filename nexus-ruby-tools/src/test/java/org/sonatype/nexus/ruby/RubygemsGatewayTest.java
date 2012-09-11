package org.sonatype.nexus.ruby;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import junit.framework.TestCase;

import org.apache.commons.io.IOUtils;
import org.jruby.embed.LocalContextScope;
import org.jruby.embed.LocalVariableBehavior;
import org.jruby.runtime.builtin.IRubyObject;
import org.junit.Before;
import org.junit.Test;

public class RubygemsGatewayTest
    extends TestCase
{
    
    private JRubyScriptingContainer scriptingContainer;
    private RubygemsGateway gateway;
    private IRubyObject check;
    
    @Before
    public void setUp() throws Exception
    {
        gateway = new DefaultRubygemsGateway();
        scriptingContainer = new JRubyScriptingContainer( LocalContextScope.SINGLETON, LocalVariableBehavior.PERSISTENT );
        check = scriptingContainer.parseFile( "nexus/check.rb" ).run();
    }
    
    @Test
    public void testGenerateGemspecRz()
        throws Exception
    {
        String gemPath = "src/test/resources/gems/n/nexus-0.1.0.gem";
        
        InputStream is = gateway.createGemspecRz( new FileInputStream( gemPath ) );
        int c = is.read();
        String gemspecPath = "target/nexus-0.1.0.gemspec.rz";
        FileOutputStream out = new FileOutputStream( gemspecPath );
        while( c != -1 )
        {
            out.write( c );
            c = is.read();
        }
        out.close();
        is.close();

        boolean equalSpecs = scriptingContainer.callMethod( check, 
                "check_gemspec_rz",
                new Object[] { gemPath, gemspecPath }, 
                Boolean.class );
        assertTrue( "spec from stream equal spec from gem", equalSpecs );
    }

    @Test
    public void testEmptySpecs() throws Exception
    {
        File empty = new File( "target/empty" );
        
        dumpStream(gateway.emptyIndex(), empty);
        
        int size = scriptingContainer.callMethod( check, 
                "specs_size", 
                empty.getAbsolutePath(), 
                Integer.class ); 
        assertEquals( "specsfile size", 0, size );
    }

    @Test
    public void testAddDeleteReleasedGemToSpecs() throws Exception
    {
        File empty = new File( "src/test/resources/empty_specs" );
        File target = new File( "target/test_specs" );
        File gem = new File( "src/test/resources/gems/n/nexus-0.1.0.gem" );
        
        Object spec = gateway.spec( new FileInputStream( gem ) );
        
        // add released gem
        InputStream is = gateway.addSpec( spec, new FileInputStream( empty ), SpecsIndexType.RELEASE );
        
        dumpStream(is, target);
        
        int size = scriptingContainer.callMethod( check, 
                "specs_size", 
                target.getAbsolutePath(), 
                Integer.class ); 
        assertEquals( "specsfile size", 1, size );
    
        // delete gem
        is = gateway.deleteSpec( spec, new FileInputStream( target ) );
    
        dumpStream(is, target);
    
        size = scriptingContainer.callMethod( check, 
                "specs_size", 
                target.getAbsolutePath(), 
                Integer.class ); 
        
        assertEquals( "specsfile size", 0, size );
        
        // try adding released gem as prereleased
        is = gateway.addSpec( spec, new FileInputStream( empty ), SpecsIndexType.PRERELEASE );

        assertNull( "no change", is );

        // adding to latest
        is = gateway.addSpec( spec, new FileInputStream( empty ), SpecsIndexType.LATEST );
        
        dumpStream(is, target);
        
        size = scriptingContainer.callMethod( check, 
                "specs_size", 
                target.getAbsolutePath(), 
                Integer.class ); 
        assertEquals( "specsfile size", 1, size );
    }

    @Test
    public void testAddDeletePrereleasedGemToSpecs() throws Exception
    {
        File empty = new File( "src/test/resources/empty_specs" );
        File target = new File( "target/test_specs" );
        File gem = new File( "src/test/resources/gems/n/nexus-0.1.0.pre.gem" );
        
        Object spec = gateway.spec( new FileInputStream( gem ) );
        
        // add prereleased gem
        InputStream is = gateway.addSpec( spec, new FileInputStream( empty ), SpecsIndexType.PRERELEASE );
        
        dumpStream(is, target);
        
        int size = scriptingContainer.callMethod( check, 
                "specs_size", 
                target.getAbsolutePath(), 
                Integer.class ); 
        assertEquals( "specsfile size", 1, size );
    
        // delete gem
        is = gateway.deleteSpec( spec, new FileInputStream( target ) );
    
        dumpStream(is, target);
    
        size = scriptingContainer.callMethod( check, 
                "specs_size", 
                target.getAbsolutePath(), 
                Integer.class ); 
        
        assertEquals( "specsfile size", 0, size );
        
        // try adding prereleased gem as released
        is = gateway.addSpec( spec, new FileInputStream( empty ), SpecsIndexType.RELEASE );

        assertNull( "no change", is );

        // adding to latest
        is = gateway.addSpec( spec, new FileInputStream( empty ), SpecsIndexType.LATEST );
        
        dumpStream(is, target);
        
        size = scriptingContainer.callMethod( check, 
                "specs_size", 
                target.getAbsolutePath(), 
                Integer.class ); 
        assertEquals( "specsfile size", 1, size );
    }

    private void dumpStream(final InputStream is, File target)
            throws IOException
    {
        try
        {
            FileOutputStream output = new FileOutputStream( target );
            try
            {
                IOUtils.copy( is, output );
            }
            finally
            {
                IOUtils.closeQuietly( output );
            }
        }
        finally
        {
            IOUtils.closeQuietly( is );
        }
    }
}