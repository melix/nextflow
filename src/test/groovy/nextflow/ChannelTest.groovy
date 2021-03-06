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

package nextflow
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY

import java.nio.file.Files
import java.nio.file.Paths

import spock.lang.Specification
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ChannelTest extends Specification {

    def testGetPathAndPattern () {

        expect:
        Channel.getFolderAndPattern( '/some/file/name.txt' ) == ['/some/file/', 'name.txt']
        Channel.getFolderAndPattern( '/some/file/*.txt' ) == ['/some/file/', '*.txt']
        Channel.getFolderAndPattern( '/some/file/*' ) == ['/some/file/', '*']
        Channel.getFolderAndPattern( '/some/file/' ) == ['/some/file/', '']
        Channel.getFolderAndPattern( 'path/filename.txt' ) == ['path/', 'filename.txt']
        Channel.getFolderAndPattern( 'filename.txt' ) == ['./', 'filename.txt']
        Channel.getFolderAndPattern( './file.txt' ) == ['./', 'file.txt']

        Channel.getFolderAndPattern( '/some/file/**/*.txt' ) == ['/some/file/', '**/*.txt']

        Channel.getFolderAndPattern( 'dxfs:///some/file/**/*.txt' ) == ['dxfs:///some/file/', '**/*.txt']
        Channel.getFolderAndPattern( 'dxfs://some/file/**/*.txt' ) == ['dxfs://some/file/', '**/*.txt']
        Channel.getFolderAndPattern( 'dxfs://*.txt' ) == ['dxfs://./', '*.txt']
        Channel.getFolderAndPattern( 'dxfs:///*.txt' ) == ['dxfs:///', '*.txt']
        Channel.getFolderAndPattern( 'dxfs:///**/*.txt' ) == ['dxfs:///', '**/*.txt']
    }


    def testSingleFile() {

        when:
        def channel = Channel.fromPath('/some/file.txt')
        then:
        channel.val == Paths.get('/some/file.txt')

        when:
        channel = Channel.fromPath('/some/f{i}le.txt')
        then:
        channel.val == Paths.get('/some/f{i}le.txt')

    }


    def testGlobAlternative() {

        setup:
        def folder = Files.createTempDirectory('testFiles')
        def file1 = Files.createFile(folder.resolve('alpha.txt'))
        def file2 = Files.createFile(folder.resolve('beta.txt'))
        def file3 = Files.createFile(folder.resolve('gamma.txt'))
        def file4 = Files.createFile(folder.resolve('file4.txt'))
        def file5 = Files.createFile(folder.resolve('file5.txt'))
        def file6 = Files.createFile(folder.resolve('file66.txt'))

        when:
        def channel = Channel.fromPath("$folder/{alpha,gamma}.txt")
        then:
        channel.val == folder.resolve('alpha.txt')
        channel.val == folder.resolve('gamma.txt')
        channel.val == Channel.STOP

        when:
        channel = Channel.fromPath("$folder/file?.txt")
        then:
        channel.val == folder.resolve('file4.txt')
        channel.val == folder.resolve('file5.txt')
        channel.val == Channel.STOP

        when:
        channel = Channel.fromPath("$folder/file*.txt")
        then:
        channel.val == folder.resolve('file4.txt')
        channel.val == folder.resolve('file5.txt')
        channel.val == folder.resolve('file66.txt')
        channel.val == Channel.STOP

        cleanup:
        folder.deleteDir()

    }


    def testGlobHiddenFiles() {

        setup:
        def folder = Files.createTempDirectory('testFiles')
        def file1 = Files.createFile(folder.resolve('.alpha.txt'))
        def file2 = Files.createFile(folder.resolve('.beta.txt'))
        def file3 = Files.createFile(folder.resolve('delta.txt'))
        def file4 = Files.createFile(folder.resolve('gamma.txt'))

        when:
        def channel = Channel.fromPath("$folder/*")
        then:
        channel.val == folder.resolve('delta.txt')
        channel.val == folder.resolve('gamma.txt')
        channel.val == Channel.STOP

        when:
        channel = Channel.fromPath("$folder/.*")
        then:
        channel.val == folder.resolve('.alpha.txt')
        channel.val == folder.resolve('.beta.txt')
        channel.val == Channel.STOP


        when:
        channel = Channel.fromPath("$folder/{.*,*}", hidden: true)
        then:
        channel.val == folder.resolve('.alpha.txt')
        channel.val == folder.resolve('.beta.txt')
        channel.val == folder.resolve('delta.txt')
        channel.val == folder.resolve('gamma.txt')
        channel.val == Channel.STOP

        cleanup:
        folder.deleteDir()

    }

    def testGlobFiles() {

        setup:
        def folder = Files.createTempDirectory('testFiles')
        def file1 = Files.createFile(folder.resolve('file1.txt'))
        def file2 = Files.createFile(folder.resolve('file2.txt'))
        def file3 = Files.createFile(folder.resolve('file3.txt'))
        def file4 = Files.createFile(folder.resolve('file4.log'))
        def sub1 = Files.createDirectories(folder.resolve('sub1'))
        def file5 = Files.createFile(sub1.resolve('file5.log'))
        def file6 = Files.createFile(sub1.resolve('file6.txt'))

        when:
        def channel = Channel.fromPath("$folder/*.txt")
        then:
        channel.val == file1
        channel.val == file2
        channel.val == file3
        channel.val == Channel.STOP


        when:
        def channel2 = Channel.fromPath("$folder/**.txt")
        then:
        channel2.val.toString() == file1.toString()
        channel2.val.toString() == file2.toString()
        channel2.val.toString() == file3.toString()
        channel2.val.toString() == file6.toString()
        channel2.val == Channel.STOP

        when:
        def channel3 = Channel.fromPath("$folder/sub1/**.log")
        then:
        channel3.val.toString() == file5.toString()
        channel3.val == Channel.STOP

        cleanup:
        folder.deleteDir()

    }



    def testStringEvents() {

        when:
        Channel.stringToWatchEvents('xxx')
        then:
        thrown(IllegalArgumentException)

        expect:
        Channel.stringToWatchEvents() == [ ENTRY_CREATE ]
        Channel.stringToWatchEvents('create,delete') == [ENTRY_CREATE, ENTRY_DELETE]
        Channel.stringToWatchEvents('Create , MODIFY ') == [ENTRY_CREATE, ENTRY_MODIFY]

    }

    def testFromPath() {

        setup:
        def folder = Files.createTempDirectory('testFiles')
        def file1 = Files.createFile(folder.resolve('file1.txt'))
        def file2 = Files.createFile(folder.resolve('file2.txt'))
        def file3 = Files.createFile(folder.resolve('file3.txt'))
        def sub1 = Files.createDirectories(folder.resolve('sub1'))
        def file5 = Files.createFile(sub1.resolve('file5.log'))

        when:
        def result = Channel.fromPath( folder.toAbsolutePath().toString() + '/*.txt' )
        then:
        result.val.name == 'file1.txt'
        result.val.name == 'file2.txt'
        result.val.name == 'file3.txt'
        result.val == Channel.STOP

        when:
        result = Channel.fromPath( folder.toAbsolutePath().toString() + '/*' )
        then:
        result.val.name == 'file1.txt'
        result.val.name == 'file2.txt'
        result.val.name == 'file3.txt'
        result.val == Channel.STOP


        when:
        result = Channel.fromPath( folder.toAbsolutePath().toString() + '/*', type: 'file' )
        then:
        result.val.name == 'file1.txt'
        result.val.name == 'file2.txt'
        result.val.name == 'file3.txt'
        result.val == Channel.STOP

        when:
        result = Channel.fromPath( folder.toAbsolutePath().toString() + '/*', type: 'dir' )
        then:
        result.val.name == 'sub1'
        result.val == Channel.STOP

        when:
        result = Channel.fromPath( folder.toAbsolutePath().toString() + '/*', type: 'any' )
        then:
        result.val.name == 'file1.txt'
        result.val.name == 'file2.txt'
        result.val.name == 'file3.txt'
        result.val.name == 'sub1'
        result.val == Channel.STOP


        when:
        result = Channel
                    .fromPath( folder.toAbsolutePath().toString() + '/**', type: 'file' )
                    .toSortedList()
                    .getVal()
                     .collect { it.name }
        then:
        result == ['file1.txt', 'file2.txt', 'file3.txt', 'file5.log' ]

        when:
        def result2 = Channel
                    .fromPath( folder.toAbsolutePath().toString() + '/**', type: 'file', maxDepth: 1 )
                    .toSortedList()
                    .getVal()
                    .collect { it.name }
        then:
        result2 == ['file1.txt', 'file2.txt', 'file3.txt' ]

        when:
        Channel.fromPath( folder.toAbsolutePath().toString() + '/*', xx: 'any' )
        then:
        thrown( IllegalArgumentException )

        when:
        Channel.fromPath( folder.toAbsolutePath().toString() + '/*', type: 'ciao' )
        then:
        thrown( IllegalArgumentException )

        cleanup:
        folder?.deleteDir()

    }


    def testFromPathWithLinks() {

        setup:
        def folder = Files.createTempDirectory('testFiles')
        def file1 = Files.createFile(folder.resolve('file1.txt'))
        def file2 = Files.createFile(folder.resolve('file2.txt'))
        def sub1 = Files.createDirectories(folder.resolve('sub_1'))
        def file3 = Files.createFile(sub1.resolve('file3.txt'))
        def file4 = Files.createFile(sub1.resolve('file4.txt'))
        Files.createSymbolicLink(folder.resolve('link_to_sub1'), sub1 )

        // -- by default traverse symlinks
        when:
        def result = Channel.fromPath( folder.toAbsolutePath().toString() + '/**/*.txt' ).toSortedList({it.name}).getVal().collect { it.getName() }
        then:
        result == ['file3.txt','file3.txt','file4.txt','file4.txt']

        // -- switch off symlinks traversing
        when:
        def result2 = Channel.fromPath( folder.toAbsolutePath().toString() + '/**/*.txt', followLinks: false ).toSortedList({it.name}).getVal().collect { it.getName() }
        then:
        result2 == ['file3.txt','file4.txt']

        cleanup:
        folder?.deleteDir()


    }


    def testFromPathHidden() {

        setup:
        def folder = Files.createTempDirectory('testFiles')
        Files.createFile(folder.resolve('file1.txt'))
        Files.createFile(folder.resolve('file2.txt'))
        Files.createFile(folder.resolve('.file_hidden.txt'))

        // -- by default no hidden
        when:
        def result = Channel.fromPath( folder.toAbsolutePath().toString() + '/*.txt' ).toSortedList({it.name}).getVal().collect { it.getName() }
        then:
        result == ['file1.txt','file2.txt']

        when:
        result = Channel.fromPath( folder.toAbsolutePath().toString() + '/.*.txt' ).toSortedList({it.name}).getVal().collect { it.getName() }
        then:
        result == ['.file_hidden.txt']

        when:
        result = Channel.fromPath( folder.toAbsolutePath().toString() + '/*.txt', hidden: true ).toSortedList({it.name}).getVal().collect { it.getName() }
        then:
        result == ['.file_hidden.txt', 'file1.txt','file2.txt']


        cleanup:
        folder?.deleteDir()


    }


}