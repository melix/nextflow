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

import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class DaemonConfigTest extends Specification {
    
    def testGetAttribute() {

        when:
        def cfg = new DaemonConfig('myDaemon', [x:123, y:222, '$myDaemon': [y:333] ] )
        then:
        cfg.getAttribute('x') == 123
        cfg.getAttribute('y') == 333
        cfg.getAttribute('z') == null
        cfg.getAttribute('z', 'alpha') == 'alpha'

        when:
        def env = [NXF_DAEMON_Z:'hola', NXF_DAEMON_P_Q_Z:'hello']
        cfg = new DaemonConfig('myDaemon', [x:123, y:222, '$myDaemon': [y:333] ], env )
        then:
        cfg.getAttribute('z', 'alpha') == 'hola'
        cfg.getAttribute('p.q.z', null) == 'hello'

    }


    def testGetInterfaces() {

        setup:
        def config
        List<String> addresses

        when:
        config = new DaemonConfig('x', [:])
        addresses = config.getNetworkInterfaceAddresses()
        then:
        addresses == []


        when:
        config = new DaemonConfig('x', [interface: '172.5.1.*,172.5.2.*'])
        addresses = config.getNetworkInterfaceAddresses()
        then:
        addresses == ['172.5.1.*','172.5.2.*']

    }

    def testGetNestedNames() {

        when:
        def cfg = new DaemonConfig('none', [x:1, y:2, tcp: [alpha: 'a', beta: 'b', gamma: [uno:1, due: 2]] ])
        then:
        cfg.getAttributesNames().sort() == ['x','y','tcp'].sort()
        cfg.getAttributesNames('tcp') == ['alpha','beta', 'gamma']
        cfg.getAttributesNames('tcp.gamma') == ['uno','due']

    }


    def testNestedValues() {

        when:
        def cfg = new DaemonConfig('none', [x:1, y:2, tcp: [alpha: 'a', beta: 'b', gamma: [uno:1, due: 2]] ])
        then:
        cfg.getAttribute('x') == 1
        cfg.getAttribute('tcp.alpha') == 'a'
        cfg.getAttribute('tcp.beta') == 'b'
        cfg.getAttribute('tcp.gamma') == [ uno:1, due: 2]
        cfg.getAttribute('tcp.gamma.uno') == 1
        cfg.getAttribute('tcp.gamma.due') == 2

    }



}
