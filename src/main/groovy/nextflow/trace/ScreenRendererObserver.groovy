/*
 * Copyright 2013-2018, Centre for Genomic Regulation (CRG)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nextflow.trace

import groovy.transform.CompileStatic
import nextflow.Session
import nextflow.processor.TaskHandler
import nextflow.util.Duration
import org.fusesource.jansi.Ansi
import org.fusesource.jansi.AnsiConsole
import static org.fusesource.jansi.Ansi.ansi
import static org.fusesource.jansi.Ansi.Color

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class ScreenRendererObserver implements TraceObserver {

    static class ProcessStats {
        int submitted
        int started
        int cached
        int failed
        int completed
        String hash
        boolean error
    }

    static class Event {
        String message
        long timestamp

        Event(String message) {
            this.message = message
            this.timestamp = System.currentTimeMillis()
        }

        @Override
        String toString() { return message }
    }

    private Session session

    private List<Event> errors = new ArrayList<>()

    private List<Event> warnings = new ArrayList<>()

    private Thread renderer

    private Map<String,ProcessStats> processes = new LinkedHashMap()

    private volatile boolean stopped

    private int printedLines

    private int maxNameLength

    private WorkflowStats stats

    private long startTimestamp

    private long endTimestamp

    void addWarning( String message ) {
        warnings << new Event(message)
    }

    void addError(String message ) {
        errors << new Event(message)
    }

    protected void render(dummy) {
        while(!stopped) {
            renderProcesses()
            sleep 150
        }
    }

    synchronized protected void renderProcesses() {
        int count = 0
        if( printedLines )
            AnsiConsole.out.println ansi().cursorUp(printedLines+1)

        // -- print processes
        processes.each { name, stats ->
            count++
            def str = line(name,stats)
            AnsiConsole.out.println(ansi().a(str).eraseLine())
        }
        printedLines = count

        // -- print warning
        def itr = warnings.iterator()
        while( itr.hasNext() ) {
            final event = itr.next()
            printAnsi(event.message, Color.YELLOW)
            final delta = System.currentTimeMillis()-event.timestamp
            // evict old warnings
            if( delta>60_000 )
                itr.remove()
        }

        // -- print errors
        for( Event event : errors ) {
            printAnsi(event.message, Color.RED)
        }

        if( stats ) {
            final delta = endTimestamp-startTimestamp
            def report = ""
            report += "Completed at: ${new Date(endTimestamp).format('dd-MMM-yyyy HH:mm:ss')}\n"
            report += "Duration    : ${new Duration(delta)}\n"
            report += "CPU hours   : ${stats.getComputeTimeFmt()}\n"
            report += "Succeeded   : ${stats.succeedCountFmt}\n"
            if( stats.cachedCount )
                report += "Cached      : ${stats.cachedCountFmt}\n"
            if( stats.ignoredCount )
                report += "Ignored     : ${stats.ignoredCountFmt}\n"
            if( stats.failedCount )
                report += "Failed      : ${stats.failedCountFmt}\n"

            printAnsi(report)
        }

        AnsiConsole.out.flush()
    }

    protected void printAnsi(String message, Ansi.Color color=null, boolean bold=false) {
        def fmt = ansi()
        if( color ) fmt = fmt.fg(color)
        if( bold ) fmt = fmt.bold()
        fmt = fmt.a(message)
        if( bold ) fmt = fmt.boldOff()
        if( color ) fmt = fmt.fg(Ansi.Color.DEFAULT)
        AnsiConsole.out.println(fmt.eraseLine())
    }

    protected String line(String name, ProcessStats stats) {
        final int tot = stats.submitted + stats.cached
        final int com = stats.completed + stats.cached
        def x = Math.round((float)(com / tot) * 100)
        final pct = "[${String.valueOf(x).padLeft(3)}%]".toString()
        final label = name.padRight(maxNameLength)
        final numbs = "${com} of ${tot}".toString()
        def result = "[$stats.hash] $label: $numbs"
        if( stats.cached )
            result += ", cached: $stats.cached"
        if( stats.failed )
            result += ", failed: $stats.failed"
        return result
    }


    protected ProcessStats p(String name) {
        def result = processes.get(name)
        if( !result ) {
            result = new ProcessStats()
            processes.put(name, result)
            maxNameLength = Math.max(maxNameLength, name.size())
        }
        return result
    }


    protected ProcessStats p(TaskHandler handler) {
        p(handler.task.processor.name)
    }

    void onFlowStart(Session session){
        this.session = session
        this.startTimestamp = System.currentTimeMillis()
        AnsiConsole.systemInstall()
        this.renderer = Thread.start('ScreenRendererObserver', this.&render)
    }

    void onFlowComplete(){
        this.stopped = true
        this.stats = session.isSuccess() ? session.getWorkflowStats() : null
        this.endTimestamp = System.currentTimeMillis()
        renderProcesses()
    }

    /**
     * This method is invoked before a process run is going to be submitted
     * @param handler
     */
    synchronized void onProcessSubmit(TaskHandler handler, TraceRecord trace){
        final process = p(handler)
        process.submitted++
        process.hash = handler.task.hashLog
    }

    synchronized  void onProcessStart(TaskHandler handler, TraceRecord trace){
        final process = p(handler)
        process.started++
        process.hash = handler.task.hashLog
    }

    synchronized void onProcessComplete(TaskHandler handler, TraceRecord trace){
        final process = p(handler)
        process.completed++
        process.hash = handler.task.hashLog
        if( handler.getStatusString() != 'COMPLETED' ) {
            process.failed++
            process.error = true
        }
    }

    synchronized void onProcessCached(TaskHandler handler, TraceRecord trace){
        final process = p(handler)
        process.cached++
        process.hash = handler.task.hashLog
    }

}
