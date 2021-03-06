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

package nextflow.processor
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger

import groovy.transform.InheritConstructors
import groovy.util.logging.Slf4j
import groovyx.gpars.dataflow.DataflowReadChannel
import groovyx.gpars.dataflow.operator.DataflowEventAdapter
import groovyx.gpars.dataflow.operator.DataflowOperator
import groovyx.gpars.dataflow.operator.DataflowProcessor
import groovyx.gpars.dataflow.operator.PoisonPill
import nextflow.script.EnvInParam
import nextflow.script.FileInParam
import nextflow.script.InParam
import nextflow.script.SetInParam
import nextflow.script.StdInParam
import nextflow.script.ValueInParam
import nextflow.util.CacheHelper
import nextflow.util.DockerBuilder
import nextflow.util.FileHelper
/**
 * Defines the 'merge' processing policy
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */

@Slf4j
@InheritConstructors
class MergeTaskProcessor extends TaskProcessor {

    /*
     * Collect the list of hashCode of each {@code TaskRun} object
     * required by this merge operation
     */
    private List<Long> mergeHashesList

    /*
     * The folder where the merge will be execute
     */
    private Path mergeTempFolder

    /*
     * Count the total number of {@code TaskRun} operation
     * executed by the final merge operation
     */
    private AtomicInteger mergeIndex = new AtomicInteger()

    /*
     * The script which will execute the merge operation.
     * This script is created by collecting all the single {@code TaskRun} scripts serially
     */
    private def mergeScript = new StringBuilder()

    /* Collect all the input values used by executing this task */
    private Map<InParam,List<FileHolder>> filesCollector = [:]

    private List<InParam> inputsDefs = []

    /* The reference to the last used context map */
    private volatile Map contextMap

    /*
     * The merge task is composed by two operator, the first creates a single 'script' to be executed by second one
     */
    @Override
    protected void createOperator() {
        log.debug "Starting merge > ${name}"

        /*
         * traverse the params definitions extracting the param of type 'set' to its basic types
         */
        taskConfig.inputs.each { InParam param ->
            if( param instanceof SetInParam )
                param.inner.each { inputsDefs << it }
            else
                inputsDefs << param
        }

        // initialize the output values collector
        inputsDefs.each { InParam param -> filesCollector[param] = [] }

        def wrapper = createCallbackWrapper(taskConfig.inputs.size(), this.&mergeScriptCollector)
        mergeHashesList = new LinkedList<>()
        mergeTempFolder = FileHelper.createTempFolder(session.workDir)

        def inChannels = new ArrayList(taskConfig.inputs.getChannels())
        def outChannels = new ArrayList(taskConfig.outputs.getChannels())
        def params = [inputs: inChannels, outputs: outChannels, listeners: [new MergeProcessorInterceptor()] ]
        processor = new DataflowOperator(group, params, wrapper)
        session.allProcessors.add(processor)

        // -- start it
        processor.start()
    }

    protected void collectOutputs( TaskRun task ) {
        collectOutputs( task, task.getTargetDir(), task.@stdout, contextMap )
    }

    protected void mergeTaskRun(TaskRun task) {

        task.stagedProvider = this.&stagedProvider

        // -- create the unique hash number for this tasks,
        //    collecting the id of all the executed runs
        //    and sorting them
        final hashMode = taskConfig.getHashMode()
        def hasher = CacheHelper.hasher(session.uniqueId, hashMode)
        mergeHashesList.sort()
        mergeHashesList.each { Long entry ->  hasher = CacheHelper.hasher(hasher,entry) }
        def hash = hasher.hash()
        log.trace "Merging process > $name -- hash: $hash"

        checkCachedOrLaunchTask( task, hash, resumable, RunType.MERGE )
    }

    protected void mergeScriptCollector( List values ) {
        final currentIndex = mergeIndex.incrementAndGet()
        log.info "Collecting process > ${name} ($currentIndex)"

        // -- the script evaluation context map
        contextMap = [:]

        // -- map the inputs to a map and use to delegate closure values interpolation
        def keys = []
        def stdin = null
        Map<FileInParam,List<FileHolder>> filesMap = [:]
        Map<String,String> environment = [:]
        int count = 0

        def secondPass = [:]
        inputsDefs.each { InParam param ->

            def val = decodeInputValue(param, values)

            // define the *context* against which the script will be evaluated
            switch( param ) {
                case ValueInParam:
                    contextMap[param.name] = val
                    break

                case StdInParam:
                    stdin = val
                    break

                case FileInParam:
                    secondPass[param] = val
                    return // <-- leave here because we do not want to add it to the keys in this loop

                case EnvInParam:
                    // the environment variables for this 'iteration'
                    environment[param.name] = val
                    break

                default:
                    log.debug "Process $name > unknown input param type: ${param?.class?.simpleName}"
            }

            // add all the input name-value pairs to the key generator
            keys << param.name << val
        }

        secondPass.each { FileInParam param, val ->
            // all the files to be staged
            def fileParam = (FileInParam)param
            def normalized = normalizeInputToFiles(val,count)
            def resolved = expandWildcards( fileParam.getFilePattern(contextMap), normalized )

            filesMap[fileParam] = resolved
            count += resolved.size()
            // set the context
            contextMap[param.name] = singleItemOrList( resolved )
            // store all the inputs
            filesCollector.get(param).add(resolved)
            // set to *val* so that the list is added to the map of all inputs
            val = resolved

            // add all the input name-value pairs to the key generator
            keys << param.name << val
        }


        /*
         * initialize the task code to be executed
         */
        Closure scriptClosure = this.code.clone() as Closure
        scriptClosure.delegate = new DelegateMap(this,contextMap)
        scriptClosure.setResolveStrategy(Closure.DELEGATE_FIRST)

        def hashMode = taskConfig.getHashMode()
        def script = getScriptlet(scriptClosure)
        def commandToRun = normalizeScript(script)
        def interpreter = fetchInterpreter(commandToRun)
        String dockerContainer = taskConfig.container

        /*
         * create a unique hash-code for this task run and save it into a list
         * which maintains all the hashes for executions making-up this merge task
         */
        keys << commandToRun << 7
        mergeHashesList << CacheHelper.hasher(keys, hashMode).hash().asLong()

        // section marker
        mergeScript << "# task '$name' ($currentIndex)" << '\n'

        // add the files to staged
        if( filesMap ) {
            mergeScript << executor.stagingFilesScript(filesMap)
        }

        // add the variables to be exported
        if( environment && !dockerContainer ) {
            mergeScript << bashEnvironmentScript(environment)
        }

        /*
         * save the script to execute into a separate unique-named file
         */
        final index = currentIndex
        final scriptName = ".merge_command.sh.${index.toString().padLeft(4,'0')}"
        final scriptFile = mergeTempFolder.resolve(scriptName)
        scriptFile.text = commandToRun

        // the command to launch this command
        def scriptCommand = scriptName

        // check if some input have to be send
        if( stdin ) {
            final inputName = ".merge_command.input.$index"
            final inputFile = mergeTempFolder.resolve( inputName )
            inputFile.text = stdin

            // pipe the user input to the user command
            scriptCommand = "$scriptCommand < ${inputName}"

            // stage the input file
            mergeScript << executor.stageInputFileScript(inputFile, inputName) << '\n'
        }

        // stage this script itself
        mergeScript << executor.stageInputFileScript(scriptFile, scriptName) << '\n'

        // check if it has to be execute through a Docker container
        if( dockerContainer ) {
            def docker = new DockerBuilder(dockerContainer)
                    .addMountForInputs( filesMap )
                    .addMount(mergeTempFolder)
                    .addMount(session.workDir)
                    .addMount(session.binDir)

            /*
             * Add the docker file -- This is a bit tricky:
             * Check if some environment variables are defined at process level
             * if so they will be saved by the BashWrapperBuilder by using the file name defined by TaskRun.CMD_ENV
             * so here add that file to the list of environment variables to be evaluated
             */
            if( getProcessEnvironment() )  {
                docker.addEnv( Paths.get(TaskRun.CMD_ENV) )
            }

            /*
             * add the environment variables for this script 'iteration'
             */
            if( environment )
                docker.addEnv(environment)

            mergeScript << (docker.build()) << ' '
        }

        // create a unique script collecting all the commands
        mergeScript << interpreter << ' ' << scriptCommand << '\n'

    }

    protected Map<InParam,List<FileHolder>> stagedProvider() { filesCollector }


    /**
     * A task of type 'merge' binds the output when it terminates it's work, i.e. when
     * it receives a 'poison pill message that will stop it
     */
    class MergeProcessorInterceptor extends DataflowEventAdapter {


        @Override
        public List<Object> beforeRun(final DataflowProcessor processor, final List<Object> messages) {
            log.trace "<${name}> Before run -- messages: ${messages}"
            return messages;
        }

        @Override
        void afterRun(DataflowProcessor processor, List<Object> messages ) {
            log.trace "<${name}> After run -- messages: ${messages}"
        }

        @Override
        public void afterStop(final DataflowProcessor processor) {
            log.debug "<${name}> After stop"
        }

        @Override
        public Object messageArrived(final DataflowProcessor processor, final DataflowReadChannel<Object> channel, final int index, final Object message) {
            log.trace "<${name}> Received message -- channel: $index; value: $message"
            return message
        }

        public Object controlMessageArrived(final DataflowProcessor processor, final DataflowReadChannel<Object> channel, final int index, final Object message) {
            if( log.isTraceEnabled() ) {
                def inputs = taskConfig.inputs.names
                log.trace "<${name}> Control message arrived -- ${inputs[index]} => ${message}"
            }

            // intercepts 'poison pill' message e.g. termination control message
            // in order the launch the task execution on the underlying system
            if( message == PoisonPill.instance )  {
                log.debug "<${name}> Poison pill arrived"

                if( mergeIndex.get()>0 ) {
                    state.update { StateObj it ->
                        it.incSubmitted()    // increment the number of received message
                        it.poison();     // signal that the poison pill has arrived
                    }

                    def task = MergeTaskProcessor.this.createTaskRun()
                    try {
                        MergeTaskProcessor.this.mergeTaskRun(task)
                    }
                    catch( Throwable e ) {
                        resumeOrDie(task, e)
                    }

                    return StopQuietly.instance
                }

                log.warn "No data collected by process > $name -- Won't execute it. Something may be wrong in your execution flow"

            }

            return message
        }

        public boolean onException(final DataflowProcessor processor, final Throwable e) {
            handleException(e)
        }

    }


}
