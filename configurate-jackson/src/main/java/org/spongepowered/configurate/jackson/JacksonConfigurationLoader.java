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
package org.spongepowered.configurate.jackson;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.loader.AbstractConfigurationLoader;
import org.spongepowered.configurate.loader.CommentHandler;
import org.spongepowered.configurate.loader.CommentHandlers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Map;

/**
 * A loader for JSON-formatted configurations, using the jackson library for parsing and generation.
 */
public class JacksonConfigurationLoader extends AbstractConfigurationLoader<BasicConfigurationNode> {

    /**
     * Creates a new {@link JacksonConfigurationLoader} builder.
     *
     * @return A new builder
     */
    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builds a {@link JacksonConfigurationLoader}.
     */
    public static class Builder extends AbstractConfigurationLoader.Builder<Builder> {
        private final JsonFactoryBuilder factory = new JsonFactoryBuilder();
        private int indent = 2;
        private FieldValueSeparatorStyle fieldValueSeparatorStyle = FieldValueSeparatorStyle.SPACE_AFTER;

        protected Builder() {
            factory.enable(JsonReadFeature.ALLOW_JAVA_COMMENTS);
            factory.enable(JsonReadFeature.ALLOW_YAML_COMMENTS);
            factory.enable(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER);
            factory.enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES);
            factory.enable(JsonReadFeature.ALLOW_SINGLE_QUOTES);
            factory.enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS);
            factory.enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS);
        }

        /**
         * Gets the {@link JsonFactory} used to configure the implementation.
         *
         * @return The json factory
         */
        @NonNull
        public JsonFactoryBuilder getFactoryBuilder() {
            return this.factory;
        }

        /**
         * Sets the level of indentation the resultant loader should use.
         *
         * @param indent The indent level
         * @return This builder (for chaining)
         */
        @NonNull
        public Builder setIndent(int indent) {
            this.indent = indent;
            return this;
        }

        /**
         * Gets the level of indentation to be used by the resultant loader.
         *
         * @return The indent level
         */
        public int getIndent() {
            return this.indent;
        }

        /**
         * Sets the field value separator style the resultant loader should use.
         *
         * @param style The style
         * @return  This builder (for chaining)
         */
        @NonNull
        public Builder setFieldValueSeparatorStyle(@NonNull FieldValueSeparatorStyle style) {
            this.fieldValueSeparatorStyle = style;
            return this;
        }

        /**
         * Gets the field value separator style to be used by the resultant loader.
         *
         * @return The style
         */
        @NonNull
        public FieldValueSeparatorStyle getFieldValueSeparatorStyle() {
            return fieldValueSeparatorStyle;
        }

        @NonNull
        @Override
        public JacksonConfigurationLoader build() {
            return new JacksonConfigurationLoader(this);
        }
    }

    private final JsonFactory factory;
    private final int indent;
    private final FieldValueSeparatorStyle fieldValueSeparatorStyle;

    private JacksonConfigurationLoader(Builder builder) {
        super(builder, new CommentHandler[]{CommentHandlers.DOUBLE_SLASH, CommentHandlers.SLASH_BLOCK, CommentHandlers.HASH});
        this.factory = builder.getFactoryBuilder().build();
        this.factory.disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);
        this.indent = builder.getIndent();
        this.fieldValueSeparatorStyle = builder.getFieldValueSeparatorStyle();
    }

    @Override
    protected void loadInternal(BasicConfigurationNode node, BufferedReader reader) throws IOException {
        try (JsonParser parser = factory.createParser(reader)) {
            parser.nextToken();
            parseValue(parser, node);
        }
    }

    private static void parseValue(JsonParser parser, ConfigurationNode node) throws IOException {
        JsonToken token = parser.getCurrentToken();
        switch (token) {
            case START_OBJECT:
                parseObject(parser, node);
                break;
            case START_ARRAY:
                parseArray(parser, node);
                break;
            case VALUE_NUMBER_FLOAT:
                double doubleVal = parser.getDoubleValue();
                if ((float)doubleVal != doubleVal) {
                    node.setValue(parser.getDoubleValue());
                } else {
                    node.setValue(parser.getFloatValue());
                }
                break;
            case VALUE_NUMBER_INT:
                long longVal = parser.getLongValue();
                if ((int)longVal != longVal) {
                    node.setValue(parser.getLongValue());
                } else {
                    node.setValue(parser.getIntValue());
                }
                break;
            case VALUE_STRING:
                node.setValue(parser.getText());
                break;
            case VALUE_TRUE:
            case VALUE_FALSE:
                node.setValue(parser.getBooleanValue());
                break;
            case VALUE_NULL: // Ignored values
            case FIELD_NAME:
                break;
            default:
                throw new IOException("Unsupported token type: " + token + " (at " + parser.getTokenLocation() + ")");
        }
    }

    private static void parseArray(JsonParser parser, ConfigurationNode node) throws IOException {
        boolean written = false;
        JsonToken token;
        while ((token = parser.nextToken()) != null) {
            switch (token) {
                case END_ARRAY:
                    // ensure the type is preserved
                    if (!written) {
                        node.setValue(ImmutableList.of());
                    }
                    return;
                default:
                    parseValue(parser, node.appendListNode());
                    written = true;
            }
        }
        throw new JsonParseException(parser, "Reached end of stream with unclosed array!", parser.getCurrentLocation());
    }

    private static void parseObject(JsonParser parser, ConfigurationNode node) throws IOException {
        boolean written = false;
        JsonToken token;
        while ((token = parser.nextToken()) != null) {
            switch (token) {
                case END_OBJECT:
                    // ensure the type is preserved
                    if (!written) {
                        node.setValue(ImmutableMap.of());
                    }
                    return;
                default:
                    parseValue(parser, node.getNode(parser.getCurrentName()));
                    written = true;
            }
        }
        throw new JsonParseException(parser, "Reached end of stream with unclosed array!", parser.getCurrentLocation());
    }

    @Override
    public void saveInternal(ConfigurationNode node, Writer writer) throws IOException {
        try (JsonGenerator generator = factory.createGenerator(writer)) {
            generator.setPrettyPrinter(new ConfiguratePrettyPrinter(indent, fieldValueSeparatorStyle));
            generateValue(generator, node);
            generator.flush();
            writer.write(SYSTEM_LINE_SEPARATOR); // Jackson doesn't add a newline at the end of files by default
        }
    }

    @NonNull
    @Override
    public BasicConfigurationNode createEmptyNode(@NonNull ConfigurationOptions options) {
        options = options.withAcceptedTypes(ImmutableSet.of(Map.class, List.class, Double.class, Float.class,
                Long.class, Integer.class, Boolean.class, String.class, byte[].class));
        return BasicConfigurationNode.root(options);
    }

    private static void generateValue(JsonGenerator generator, ConfigurationNode node) throws IOException {
        if (node.isMap()) {
            generateObject(generator, node);
        } else if (node.isList()) {
            generateArray(generator, node);
        } else {
            Object value = node.getValue();
            if (value instanceof Double) {
                generator.writeNumber((Double) value);
            } else if (value instanceof Float) {
                generator.writeNumber((Float) value);
            } else if (value instanceof Long) {
                generator.writeNumber((Long) value);
            } else if (value instanceof Integer) {
                generator.writeNumber((Integer) value);
            } else if (value instanceof Boolean) {
                generator.writeBoolean((Boolean) value);
            } else if (value instanceof byte[]) {
                generator.writeBinary((byte[]) value);
            } else {
                generator.writeString(value.toString());
            }
        }
    }

    /*private void generateComment(JsonGenerator generator, ConfigurationNode node, boolean inArray) throws IOException {
        if (node instanceof CommentedConfigurationNode) {
            final Optional<String> comment = ((CommentedConfigurationNode) node).getComment();
            if (comment.isPresent()) {
                if (indent == 0) {
                    generator.writeRaw("/*");
                    generator.writeRaw(comment.get().replaceAll("\\* /", ""));
                    generator.writeRaw("* /");
                } else {
                    for (Iterator<String> it = LINE_SPLITTER.split(comment.get()).iterator(); it.hasNext();) {
                        generator.writeRaw("// ");
                        generator.writeRaw(it.next());
                        generator.getPrettyPrinter().beforeObjectEntries(generator);
                        if (it.hasNext()) {
                            generator.writeRaw(SYSTEM_LINE_SEPARATOR);
                        }
                    }
                    if (inArray) {
                        generator.writeRaw(SYSTEM_LINE_SEPARATOR);
                    }
                }
            }
        }
    }*/

    private static void generateObject(JsonGenerator generator, ConfigurationNode node) throws IOException {
        if (!node.isMap()) {
            throw new IOException("Node passed to generateObject does not have map children!");
        }
        generator.writeStartObject();
        for (Map.Entry<Object, ? extends ConfigurationNode> ent : node.getChildrenMap().entrySet()) {
            //generateComment(generator, ent.getValue(), false);
            generator.writeFieldName(ent.getKey().toString());
            generateValue(generator, ent.getValue());
        }
        generator.writeEndObject();
    }

    private static void generateArray(JsonGenerator generator, ConfigurationNode node) throws IOException {
        if (!node.isList()) {
            throw new IOException("Node passed to generateArray does not have list children!");
        }
        List<? extends ConfigurationNode> children = node.getChildrenList();
        generator.writeStartArray(children.size());
        for (ConfigurationNode child : children) {
            //generateComment(generator, child, true);
            generateValue(generator, child);
        }
        generator.writeEndArray();
    }
}
