package groovy.runtime.metaclass;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import groovy.lang.Closure;
import groovy.lang.MetaClass;
import groovy.lang.MissingMethodException;
import groovyx.gpars.dataflow.DataflowReadChannel;
import groovyx.gpars.dataflow.DataflowWriteChannel;
import nextflow.splitter.SplitterFactory;
import nextflow.util.FileHelper;

/**
 * Provides the "dynamic" splitter methods and {@code isEmpty} method for {@link File} and {@link Path} classes.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class NextflowDelegatingMetaClass extends groovy.lang.DelegatingMetaClass {

    public NextflowDelegatingMetaClass(MetaClass delegate) {
        super(delegate);
    }

    @Override
    public Object invokeMethod(Object obj, String methodName, Object[] args)
    {
        int len = args != null ? args.length : 0;

        /*
         * Implements the 'isEmpty' method for {@link Path} and {@link File} objects.
         * It cannot implemented as a extension method since Path has a private 'isEmpty' method
         *
         * See http://jira.codehaus.org/browse/GROOVY-6363
         */
        if( "isEmpty".equals(methodName) && len==0 ) {
            if( obj instanceof File)
                return FileHelper.empty((File)obj);
            if( obj instanceof Path )
                return FileHelper.empty((Path)obj);
        }


        /*
         * invoke the method
         */
        try {
            return delegate.invokeMethod(obj, methodName, args);
        }

        /*
         * try to fallback on a splitter method if missing
         */
        catch( MissingMethodException e1 ) {

            // make it possible to invoke some dataflow operator specifying an open array
            // in place of a list object. For example:
            // DataflowReadChannel# separate(final List<DataflowWriteChannel<?>> outputs, final Closure<List<Object>> code)
            // can be invoked as:
            // queue.separate( x, y, z ) { ... }
            if( checkOpenArrayDataflowMethod(NAMES, obj, methodName, args) ) {
                Object[] newArgs = new Object[] { toListOfChannel(args), args[len - 1] };
                return delegate.invokeMethod(obj, methodName, newArgs);
            }

            return SplitterFactory.tryFallbackWithSplitter(obj, methodName, args, e1);
        }
    }

    static private final List<String> NAMES = Arrays.asList("choice","merge","separate");

    /**
     *  Check teh following conditions:
     *  <li>method name is the requested one
     *  <li>the target object is a {@link groovyx.gpars.dataflow.DataflowReadChannel}
     *  <li>the all items in the last arguments array are {@link groovyx.gpars.dataflow.DataflowWriteChannel}
     *  <li>the last item in the arguments array is a {@link Closure}
     */
    private static boolean checkOpenArrayDataflowMethod(List<String> validNames, Object obj, String methodName, Object[] args) {
        if( !validNames.contains(methodName)  ) return false;
        if( !(obj instanceof DataflowReadChannel)) return false;
        if( args == null || args.length<2 ) return false;
        if( !(args[args.length-1] instanceof Closure) ) return false;
        for( int i=0; i<args.length-1; i++ )
            if( !(args[i] instanceof DataflowWriteChannel )) return false;

        return true;
    }


    /**
     * Given an array of arguments copy the first n-1 to a list of {@link groovyx.gpars.dataflow.DataflowWriteChannel}
     */
    private static List<DataflowWriteChannel> toListOfChannel( Object[] args )  {
        List<DataflowWriteChannel> result = new ArrayList<>(args.length-1);
        for( int i=0; i<args.length-1; i++ ) {
            result.add(i, (DataflowWriteChannel)args[i]);
        }
        return result;
    }


}
