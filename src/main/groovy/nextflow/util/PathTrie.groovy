/*
 * Copyright (c) 2013-2014, Centre for Genomic Regulation (CRG).
 * Copyright (c) 2013-2014, Paolo Di Tommaso and the respective authors.
 *
 *   This file is part of 'Nextflow'.
 *
 *   Nextflow is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   Nextflow is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with Nextflow.  If not, see <http://www.gnu.org/licenses/>.
 */

package nextflow.util
import java.nio.file.Path
/**
 * A trie data structure specialised to find the longest common paths
 * in a given list of paths
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class PathTrie {

    private static PATH_SEP = File.separator

    List<Trie<String>> paths = []

    PathTrie() {}

    /**
     * Get or create a not for the given path item.
     *
     * @param item A path item represented as string
     * @return A {@code Trie<String>} for the given path item
     */
    protected Trie<String> getOrCreate( String item ) {
        def found = paths.find { Trie it -> it.vertex == item }
        if( !found ) {
            found = new Trie<String>(item)
            paths << found
        }
        return found
    }

    /**
     * Add a path to the trie collection
     *
     * @param path The path to add, it can an absolute or relative path
     */
    void add( Path path )  {
        assert path

        List<String> tokens = path.collect { it.name }
        if( !tokens )
            return

        def head = tokens.head()
        if( path.isAbsolute() )
            head = PATH_SEP + head
        getOrCreate(head).append( tokens.tail() )
    }

    /**
     * Add a file to the collection of path
     *
     * @param file
     */
    void add( File file ) {
        add(file.toPath())
    }

    /**
     * Add a string path to the collection
     *
     * @param path
     */
    void add( String path ) {
        add( FileHelper.asPath(path) )
    }

    /**
     * Retrieve the list of the longest paths. For example, given:
     *
     * <pre>
     *  /home/data/work
     *  /home/data/work/xx/file_x
     *  /db/data/tutorial
     *  /db/data/xxx
     * </pre>
     *
     * Il returns a list containing two paths:
     *
     * <pre>
     *     /home/data/work
     *     /db/data
     * </pre>
     *
     * @return
     */
    List<String> longest() {

        def result = new LinkedList<String>()
        paths.each {

            def tokens = it.longest()
            if( !tokens )
                return

            result.add(tokens.join(PATH_SEP))
        }

        return result
    }



}
