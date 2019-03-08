/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.versions

import groovy.util.slurpersupport.GPathResult
import groovy.util.slurpersupport.NodeChildren
import spock.lang.Unroll

class ConsistentPlatformPluginIntegrationSpec extends GradleVersionIntegrationTestKitSpec {

    static def PLUGIN_NAME = "com.palantir.consistent-platform"

    void setup() {
        keepFiles = true
        settingsFile.createNewFile()
        buildFile << """
            plugins { id 'com.palantir.versions-lock' }
        """.stripIndent()
        addSubproject('bom', """plugins { id '$PLUGIN_NAME' }\n""")
    }

    def "test publishes constraints from lock file"() {
        file('versions.lock') << """\
            org:a:1.0 (1 constraints: 0000000)
            org:b:1.3 (1 constraints: 0000000)
        """.stripIndent()

        when:
        runTasks('bom:generatePomFileForBomPublication')

        then:
        def output = new File('bom/build/publications/bom/pom-default.xml', projectDir)
        output.exists()

        def slurper = new XmlSlurper()
        def pom = slurper.parse(output)
        NodeChildren dependencies = pom.dependencyManagement.dependencies.dependency
        def expected = [[groupId   : 'org',
                         artifactId: 'a',
                         version   : '1.0',
                         scope     : 'compile',],
                        [groupId   : 'org',
                         artifactId: 'b',
                         version   : '1.3',
                         scope     : 'compile',]] as Set
        dependencies.collect { convertToMap(it) } as Set == expected
    }

    def "excludes forces from versions.props"() {
        file('versions.lock') << """\
            org:a:1.0 (1 constraints: 0000000)
        """.stripIndent()

        buildFile << """
            plugins { id 'com.palantir.consistent-versions' }
        """.stripIndent()

        file('versions.props') << """\
            dont:want-this = 1.0
        """.stripIndent()

        when:
        runTasks('bom:generatePomFileForBomPublication')

        then:
        def output = new File('bom/build/publications/bom/pom-default.xml', projectDir)
        output.exists()

        def slurper = new XmlSlurper()
        def pom = slurper.parse(output)
        NodeChildren dependencies = pom.dependencyManagement.dependencies.dependency
        def expected = [[groupId   : 'org',
                         artifactId: 'a',
                         version   : '1.0',
                         scope     : 'compile',],] as Set
        dependencies.collect { convertToMap(it) } as Set == expected
    }

    @Unroll
    def 'includes other published projects for Gradle #gradleVersion'() {
        this.gradleVersion = gradleVersion
        file('versions.lock') << """\
            org:a:1.0 (1 constraints: 0000000)
        """.stripIndent()

        buildFile << """
            plugins { id 'com.palantir.consistent-versions' }
            allprojects {
                group = 'project'
                version = '0.0.1'
            }
        """.stripIndent()

        addSubproject('published', """
            apply plugin: 'maven-publish'
            publishing.publications {
                whatever(MavenPublication)
            }
        """.stripIndent())

        addSubproject('unpublished', '')

        when:
        runTasks('bom:generatePomFileForBomPublication')

        then:
        def output = new File('bom/build/publications/bom/pom-default.xml', projectDir)
        output.exists()

        def slurper = new XmlSlurper()
        def pom = slurper.parse(output)
        NodeChildren dependencies = pom.dependencyManagement.dependencies.dependency
        def expected = [[groupId   : 'org',
                         artifactId: 'a',
                         version   : '1.0',
                         scope     : 'compile',],
                        [groupId   : 'project',
                         artifactId: 'published',
                         version   : '0.0.1',
                         scope     : 'compile',]] as Set
        dependencies.collect { convertToMap(it) } as Set == expected

        where:
        gradleVersion << ['5.2', null]
    }

    @Unroll
    def "includes bom dependencies from rootConfiguration for Gradle #gradleVersion"() {
        this.gradleVersion = gradleVersion
        file('versions.lock') << """\
            org:a:1.0 (1 constraints: 0000000)
            org:platform:2.0 (1 constraints: 0000000)
        """.stripIndent()

        buildFile << """
            plugins { id 'com.palantir.consistent-versions' }
            allprojects {
                dependencies {
                    rootConfiguration platform("org:platform")
                }
            }
        """.stripIndent()

        file('versions.props') << """\
            dont:want-this = 1.0
        """.stripIndent()

        when:
        runTasks('bom:generatePomFileForBomPublication')

        then:
        def output = new File('bom/build/publications/bom/pom-default.xml', projectDir)
        output.exists()

        def slurper = new XmlSlurper()
        def pom = slurper.parse(output)
        NodeChildren dependencies = pom.dependencyManagement.dependencies.dependency
        def expected = [[groupId   : 'org',
                         artifactId: 'a',
                         version   : '1.0',
                         scope     : 'compile',],
                        [groupId   : 'org',
                         artifactId: 'platform',
                         version   : '2.0',
                         type      : 'pom',
                         scope     : 'import',]] as Set
        dependencies.collect { convertToMap(it) } as Set == expected

        where:
        gradleVersion << ['5.2', null]
    }

    /**
     * Recursively converts a node's children to a map of <tt>(tag name): (value inside tag)</tt>.
     * <p>
     * See: https://stackoverflow.com/a/26889997/274699
     */
    def convertToMap(GPathResult node) {
        node.children().collectEntries {
            [ it.name(), it.childNodes() ? convertToMap(it) : it.text() ]
        }
    }
}