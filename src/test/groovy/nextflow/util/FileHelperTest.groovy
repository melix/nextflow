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

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import spock.lang.Specification
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class FileHelperTest extends Specification {

    def 'test asPath' () {

        expect:
        FileHelper.asPath('file.txt') == Paths.get('file.txt')
        FileHelper.asPath('file:///file.txt') == Paths.get( URI.create('file:///file.txt') )

    }

    def 'test normalize' () {

        expect:
        FileHelper.normalizePath( 'file.name' ) == 'file.name'
        FileHelper.normalizePath( '~file.name' ) == '~file.name'
        FileHelper.normalizePath( '~' ) == System.properties['user.home']
        FileHelper.normalizePath( '~/path/file.name' ) == System.properties['user.home'] + '/path/file.name'

    }

    def 'test isEmpty file'() {

        setup:
        def emptyFile = File.createTempFile('test','test')
        def notEmptyFile = File.createTempFile('test','test')
        notEmptyFile.text = 'HOLA'
        emptyFile.deleteOnExit()
        notEmptyFile.deleteOnExit()

        expect:
        FileHelper.empty(emptyFile)
        !FileHelper.empty(notEmptyFile)

    }

    def 'test isEmpty dir'() {

        setup:
        def emptyDir = File.createTempDir()
        def notEmptyDir = File.createTempDir()
        File.createTempFile('test','test', notEmptyDir)

        expect:
        FileHelper.empty(emptyDir)
        !FileHelper.empty(notEmptyDir)

        cleanup:
        emptyDir.deleteDir()
        notEmptyDir.deleteDir()

    }


    def 'test empty file' () {

        setup:
        def fileEmpty = File.createTempFile('test','test')
        fileEmpty.deleteOnExit()

        File folderEmpty = File.createTempDir()

        File  folderNotEmpty = File.createTempDir()
        def fileInFolder = new File(folderNotEmpty, 'filename')
        fileInFolder.createNewFile()

        def fileNotEmpty = File.createTempFile('test','test')
        fileNotEmpty.text = 'Hola'
        fileNotEmpty.deleteOnExit()

        expect:
        FileHelper.empty(new File('non existing'))
        FileHelper.empty(fileEmpty)
        !FileHelper.empty(fileNotEmpty)
        FileHelper.empty(folderEmpty)
        !FileHelper.empty(folderNotEmpty)

        cleanup:
        fileEmpty.delete()
        folderNotEmpty?.deleteDir()
        folderEmpty?.deleteDir()

    }

    def 'test empty path' () {

        given:
        Path baseFolder = Files.createTempDirectory('empty')

        Path fileEmpty = Files.createTempFile(baseFolder, 'test','txt')
        Path folderEmpty = Files.createTempDirectory(baseFolder, null)
        Path folderNotEmpty = Files.createTempDirectory(baseFolder, null)
        Path fileInFolder = folderNotEmpty.resolve( 'empty_file' )
        Files.createFile(fileInFolder)

        Path fileNotEmpty = folderNotEmpty.resolve('not_empty_file')
        fileNotEmpty.text = 'Hola'

        Path fileNotExist = Paths.get('not_existing_file')

        expect:
        FileHelper.empty(fileNotExist)
        FileHelper.empty(fileEmpty)
        !FileHelper.empty(fileNotEmpty)
        FileHelper.empty(folderEmpty)
        !FileHelper.empty(folderNotEmpty)

        cleanup:
        baseFolder?.deleteDir()

    }



    def 'test nameParts' () {

        expect:
        FileHelper.nameParts("hola") == ['hola',0]
        FileHelper.nameParts("hola123")  == ['hola',123]
        FileHelper.nameParts("hola1")  == ['hola',1]
        FileHelper.nameParts("x")  == ['x',0 ]
        FileHelper.nameParts("x1")  == ['x',1 ]
        FileHelper.nameParts("1")  == ['',1]

    }



    def 'tests parent' () {

        expect:
        new File(".") .parentFile == null
        new File("hola") .parentFile == null
        new File("/").parentFile == null
        new File("/").absoluteFile == new File("/")
        new File('.').absolutePath.startsWith('/')
    }



}
