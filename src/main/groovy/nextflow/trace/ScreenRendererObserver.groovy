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
import org.fusesource.jansi.AnsiConsole
import static org.fusesource.jansi.Ansi.ansi
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
    }

    private Thread renderer

    private Map<String,ProcessStats> processes = new LinkedHashMap()

    private volatile boolean stopped

    private int printedLines

    private int maxNameLength

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

        processes.each { name, stats ->
            count++
            AnsiConsole.out.println ansi().a( line(name,stats) ).eraseLine()
        }

        printedLines = count
        AnsiConsole.out.flush()
    }

    protected String line(String name, ProcessStats stats) {
        final int tot = stats.submitted + stats.cached
        final int com = stats.completed + stats.cached
        def x = Math.round((float)(com / tot) * 100)
        final pct = "[${String.valueOf(x).padLeft(3)}%]".toString()
        final label = name.padRight(maxNameLength)
        final numbs = "${com} of ${tot}".toString()
        def result = "> $pct $label: $numbs"
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
        AnsiConsole.systemInstall()
        this.renderer = Thread.start('ScreenRendererObserver', this.&render)
    }

    void onFlowComplete(){
        stopped = true
        renderProcesses()
    }

    /**
     * This method is invoked before a process run is going to be submitted
     * @param handler
     */
    synchronized void onProcessSubmit(TaskHandler handler, TraceRecord trace){
        p(handler).submitted++
    }

    synchronized  void onProcessStart(TaskHandler handler, TraceRecord trace){
        p(handler).started++
    }

    synchronized void onProcessComplete(TaskHandler handler, TraceRecord trace){
        def process = p(handler)
        process.completed++
        if( handler.getStatusString() != 'COMPLETED' ) {
            process.failed++
        }
    }

    synchronized void onProcessCached(TaskHandler handler, TraceRecord trace){
        p(handler).cached++
    }

}
