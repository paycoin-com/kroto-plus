/*
 * Copyright 2019 Kroto+ Contributors
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

package com.github.marcoferrer.krotoplus.proto

import com.google.protobuf.ByteString
import com.google.protobuf.DescriptorProtos
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type.*
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import kotlin.reflect.KClass


fun DescriptorProtos.FieldDescriptorProto.getFieldClassName(schema: Schema): KClass<*> =
    when (type!!) {
        TYPE_INT64,
        TYPE_FIXED64,
        TYPE_SFIXED64,
        TYPE_SINT64,
        TYPE_UINT64 -> Long::class
        TYPE_INT32,
        TYPE_UINT32,
        TYPE_SFIXED32,
        TYPE_SINT32,
        TYPE_FIXED32 -> Int::class
        TYPE_DOUBLE -> Double::class
        TYPE_FLOAT -> Float::class
        TYPE_BOOL -> Boolean::class
        TYPE_STRING -> String::class
        TYPE_BYTES -> ByteString::class

        TYPE_GROUP ->
            TODO("ClassName for field type 'GROUP' is not yet implemented")

        TYPE_MESSAGE -> (schema.protoTypes[typeName] as ProtoMessage)::class
        TYPE_ENUM -> (schema.protoTypes[typeName] as ProtoEnum)::class
    }

//need to check the actual message in schema
fun DescriptorProtos.FieldDescriptorProto.isMapField(schema:Schema): Boolean =
    type == TYPE_MESSAGE && getFieldProtoMessage(schema).isMapEntry

val DescriptorProtos.FieldDescriptorProto.isRepeated: Boolean
    get() = label == LABEL_REPEATED


fun DescriptorProtos.FieldDescriptorProto.getParamaterizeTypeName(schema: Schema): ParameterizedTypeName =
    when{
        isMapField(schema) -> {
            val message = getFieldProtoMessage(schema)
            val keyCn = message.descriptorProto.getField(0)
                .getFieldClassName(schema)

            val valueCn = message.descriptorProto.getField(1)
                .getFieldClassName(schema)

            Map::class.parameterizedBy(keyCn, valueCn)
        }
        label == LABEL_REPEATED -> {
            List::class.parameterizedBy(getFieldClassName(schema))
        }
        else -> throw IllegalArgumentException("Only 'REPEATED' or 'MapEntry' fields can be parameterized")
    }


fun DescriptorProtos.FieldDescriptorProto.getFieldProtoMessage(schema: Schema): ProtoMessage =
    requireNotNull(schema.protoTypes[typeName] as? ProtoMessage){
        "$typeName was not found in schema type map."
    }


fun DescriptorProtos.FieldDescriptorProto.getDefaultInitializer(schema: Schema): String =
    when {
        isMapField(schema) -> "emptyMap()"
        isRepeated -> "emptyList()"
        else -> when (type!!) {
            TYPE_INT64,
            TYPE_FIXED64,
            TYPE_SFIXED64,
            TYPE_SINT64,
            TYPE_UINT64,
            TYPE_INT32,
            TYPE_UINT32,
            TYPE_SFIXED32,
            TYPE_SINT32,
            TYPE_FIXED32,
            TYPE_DOUBLE,
            TYPE_FLOAT -> "0"
            TYPE_BOOL -> "false"
            TYPE_STRING -> "\"\""
            TYPE_BYTES,
            TYPE_GROUP ->
                TODO("DefaultInitializer for field type '${type.name}' is not yet implemented")

            TYPE_MESSAGE -> (schema.protoTypes[typeName] as ProtoMessage)
                .let { it.canonicalJavaName + ".defaultInstance" }


            TYPE_ENUM -> (schema.protoTypes[typeName] as ProtoEnum)
                .let { enum ->
                    "${enum.canonicalJavaName}.${enum.descriptorProto.valueList.first().name}"
                }
        }
    }