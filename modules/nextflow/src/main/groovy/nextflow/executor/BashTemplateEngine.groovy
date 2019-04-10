/*
 * Copyright 2013-2019, Centre for Genomic Regulation (CRG)
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

package nextflow.executor

import java.util.regex.Pattern

import groovy.transform.CompileStatic
import nextflow.util.MustacheTemplateEngine

/**
 * Template engine for Nextflow bash wrapper script
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class BashTemplateEngine extends MustacheTemplateEngine {

    private Pattern COMMENT = ~/^ *## .+$|^##$/

    /**
     * Strips all lines starting double # character
     *
     * @param line The to be stripped or not
     * @return {@code true} when the line needs to be removed, {@code false} otherwise
     */
    @Override
    protected boolean accept(String line) {
        return !COMMENT.matcher(line).matches()
    }
}
