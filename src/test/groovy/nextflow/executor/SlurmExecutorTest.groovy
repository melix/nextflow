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

package nextflow.executor

import java.nio.file.Paths

import nextflow.processor.TaskConfig
import nextflow.processor.TaskRun
import nextflow.script.BaseScript
import nextflow.util.Duration
import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class SlurmExecutorTest extends Specification {

    def testParseJob() {

        when:
        def exec = [:] as SlurmExecutor
        then:
        exec.parseJobId('Submitted batch job 10') == '10'

    }

    def testKill() {

        given:
        def exec = [:] as SlurmExecutor
        expect:
        exec.killTaskCommand(123) == ['scancel','123']

    }

    def testGetSubmitCmdLine() {

        given:
        def base = Mock(BaseScript)
        def config = new TaskConfig(base)
        def script = Paths.get('/some/script.sh')
        def task = Mock(TaskRun)
        task.workDirectory >> Paths.get('/work/path')
        task.name >> 'task 555'
        def exec = [:] as SlurmExecutor
        exec.taskConfig = config

        when:
        config.maxDuration( Duration.of('1h') )
        config.clusterOptions = '-x -y -z'
        then:
        exec.getSubmitCommandLine(task,script).join(' ') == 'sbatch -D /work/path -J nf-task_555 -o /dev/null -t 01:00:00 -x -y -z script.sh'
    }

    def testQstatCommand() {

        setup:
        def executor = [:] as SlurmExecutor
        def text =
                """
                5 PD
                6 PD
                13 R
                14 CA
                15 F
                4 R
                """.stripIndent().trim()


        when:
        def result = executor.parseQueueStatus(text)
        then:
        result.size() == 6
        result['4'] == AbstractGridExecutor.QueueStatus.RUNNING
        result['5'] == AbstractGridExecutor.QueueStatus.PENDING
        result['6'] == AbstractGridExecutor.QueueStatus.PENDING
        result['13'] == AbstractGridExecutor.QueueStatus.RUNNING
        result['14'] == AbstractGridExecutor.QueueStatus.ERROR
        result['15'] == AbstractGridExecutor.QueueStatus.ERROR

    }

    def testQueueStatusCommand() {
        when:
        def exec = [:] as SlurmExecutor
        then:
        exec.queueStatusCommand(null) == ['squeue','-h','-o \'%i %t\'']
        exec.queueStatusCommand('xxx') == ['squeue','-h','-o \'%i %t\'']


    }
}
