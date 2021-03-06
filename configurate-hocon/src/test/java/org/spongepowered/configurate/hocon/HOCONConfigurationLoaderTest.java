/*
 * Configurate
 * Copyright (C) zml and Configurate contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.spongepowered.configurate.hocon;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junitpioneer.jupiter.TempDirectory;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.loader.AtomicFiles;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Basic sanity checks for the loader
 */
@ExtendWith(TempDirectory.class)
public class HOCONConfigurationLoaderTest {

    @Test
    public void testSimpleLoading(@TempDirectory.TempDir Path tempDir) throws IOException {
        URL url = getClass().getResource("/example.conf");
        final Path saveTest = tempDir.resolve("text1.txt");

        HOCONConfigurationLoader loader = HOCONConfigurationLoader.builder()
                .setSource(() -> new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8)))
                .setSink(AtomicFiles.createAtomicWriterFactory(saveTest, StandardCharsets.UTF_8)).build();
        CommentedConfigurationNode node = loader.load();
        assertEquals("unicorn", node.getNode("test", "op-level").getValue());
        assertEquals("dragon", node.getNode("other", "op-level").getValue());
        CommentedConfigurationNode testNode = node.getNode("test");
        assertEquals(" Test node", testNode.getComment().orElse(null));
        assertEquals("dog park", node.getNode("other", "location").getValue());
        loader.save(node);
        assertEquals(Resources.readLines(getClass().getResource("/roundtrip-test.conf"), StandardCharsets.UTF_8), Files
                .readAllLines(saveTest, StandardCharsets.UTF_8));
    }

    @Test
    public void testSplitLineCommentInput(@TempDirectory.TempDir Path tempDir) throws IOException {
        final Path saveTo = tempDir.resolve("text2.txt");
        HOCONConfigurationLoader loader = HOCONConfigurationLoader.builder()
                .setPath(saveTo)
                .setURL(getClass().getResource("/splitline-comment-input.conf"))
                .build();
        CommentedConfigurationNode node = loader.load();
        System.out.println(node.getOptions().getHeader());
        loader.save(node);

        assertEquals(Resources.readLines(getClass().getResource("/splitline-comment-output.conf"), StandardCharsets.UTF_8), Files.readAllLines(saveTo, StandardCharsets.UTF_8));
    }

    @Test
    public void testHeaderSaved(@TempDirectory.TempDir Path tempDir) throws IOException {
        final Path saveTo = tempDir.resolve("text3.txt");
        HOCONConfigurationLoader loader = HOCONConfigurationLoader.builder()
                .setPath(saveTo)
                .build();
        CommentedConfigurationNode node = loader.createEmptyNode(ConfigurationOptions.defaults().withHeader("Hi! I am a header!\n" +
                        "Look at meeeeeee!!!"));
        node.getNode("node").setComment("I have a comment").getNode("party").setValue("now");

        loader.save(node);
        assertEquals(Resources.readLines(getClass().getResource("/header.conf"), StandardCharsets.UTF_8), Files.readAllLines(saveTo, StandardCharsets.UTF_8));

    }

    @Test
    public void testBooleansNotShared(@TempDirectory.TempDir Path tempDir) throws IOException {
        URL url = getClass().getResource("/comments-test.conf");
        final Path saveTo = tempDir.resolve("text4.txt");
        HOCONConfigurationLoader loader = HOCONConfigurationLoader.builder()
                .setPath(saveTo).setURL(url).build();

        CommentedConfigurationNode node = loader.createEmptyNode(ConfigurationOptions.defaults());
        node.getNode("test", "third").setValue(false).setComment("really?");
        node.getNode("test", "apple").setComment("fruit").setValue(false);
        node.getNode("test", "donut").setValue(true).setComment("tasty");
        node.getNode("test", "guacamole").setValue(true).setComment("and chips?");

        loader.save(node);
        assertEquals(Resources.readLines(url, StandardCharsets.UTF_8), Files.readAllLines(saveTo, StandardCharsets.UTF_8));
    }

    @Test
    public void testNewConfigObject() {
        Map<String, ConfigValue> entries = ImmutableMap.of("a", ConfigValueFactory.fromAnyRef("hi"), "b", ConfigValueFactory.fromAnyRef("bye"));
        HOCONConfigurationLoader.newConfigObject(entries);
    }

    @Test
    public void testNewConfigList() {
        List<ConfigValue> entries = ImmutableList.of(ConfigValueFactory.fromAnyRef("hello"), ConfigValueFactory.fromAnyRef("goodbye"));
        HOCONConfigurationLoader.newConfigList(entries);
    }
}
