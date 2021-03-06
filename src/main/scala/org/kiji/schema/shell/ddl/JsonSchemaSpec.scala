/**
 * (c) Copyright 2013 WibiData, Inc.
 *
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
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

package org.kiji.schema.shell.ddl

import java.util.ArrayList

import org.apache.avro.Schema

import org.kiji.annotations.ApiAudience
import org.kiji.schema.avro.AvroValidationPolicy
import org.kiji.schema.avro.CellSchema
import org.kiji.schema.avro.SchemaStorage
import org.kiji.schema.avro.SchemaType
import org.kiji.schema.shell.DDLException

/** A schema specified as its Avro json representation. */
@ApiAudience.Private
final class JsonSchemaSpec(val json: String) extends SchemaSpec {
  override def toString(): String = { json }

  override def toNewCellSchema(cellSchemaContext: CellSchemaContext): CellSchema = {
    if (cellSchemaContext.supportsLayoutValidation()) {
      // layout-1.3 and up: use validation-enabled specification.
      val avroValidationPolicy: AvroValidationPolicy =
          cellSchemaContext.getValidationPolicy().avroValidationPolicy

      val avroSchema: Schema = new Schema.Parser().parse(json)

      // Use the schema table to find the actual uid associated with this schema.
      val uidForSchema: Long = cellSchemaContext.env.kijiSystem.getOrCreateSchemaId(
          cellSchemaContext.env.instanceURI, avroSchema)

      // Register the specified class as a valid reader and writer schema as well as the
      // default reader schema.
      val readers: ArrayList[java.lang.Long] = new ArrayList()
      readers.add(uidForSchema)

      val writers: ArrayList[java.lang.Long] = new ArrayList()
      writers.add(uidForSchema)

      // For now (layout-1.3), adding to the writers list => adding to the "written" list.
      val written: ArrayList[java.lang.Long] = new ArrayList()
      written.add(uidForSchema)

      return CellSchema.newBuilder()
          .setStorage(SchemaStorage.UID)
          .setType(SchemaType.AVRO)
          .setValue(null)
          .setAvroValidationPolicy(avroValidationPolicy)
          .setDefaultReader(uidForSchema)
          .setReaders(readers)
          .setWritten(written)
          .setWriters(writers)
          .build()
    } else {
      // layout-1.2 and prior; use older specification.
      val schema = CellSchema.newBuilder
      schema.setType(SchemaType.INLINE)
      schema.setStorage(SchemaStorage.UID)
      schema.setValue(json)
      return schema.build()
    }
  }

  override def addToCellSchema(cellSchema: CellSchema, cellSchemaContext: CellSchemaContext):
      CellSchema = {
    if (!cellSchemaContext.supportsLayoutValidation()) {
      // This method was called in an inappropriate context; the CellSchema
      // should be overwritten entirely in older layouts.
      throw new DDLException("SchemaSpec.addToCellSchema() can only be used on validating layouts")
    }

    val avroSchema: Schema = new Schema.Parser().parse(json)

    // Add to the main reader/writer/etc lists.
    addAvroToCellSchema(avroSchema, cellSchema, cellSchemaContext.schemaUsageFlags,
        cellSchemaContext.env)

    return cellSchema
  }

  override def dropFromCellSchema(cellSchema: CellSchema, cellSchemaContext: CellSchemaContext):
      CellSchema = {

    if (!cellSchemaContext.supportsLayoutValidation()) {
      // This method was called in an inappropriate context; the CellSchema
      // should be overwritten entirely in older layouts.
      throw new DDLException("SchemaSpec.dropFromCellSchema() can only be used "
          + "on validating layouts")
    }

    val avroSchema: Schema = new Schema.Parser().parse(json)

    // Use the schema table to find the actual uid associated with this schema.
    // If this returns "None" then the schema never existed -- do nothing.
    val maybeUidForSchemaClass: Option[Long] =
        cellSchemaContext.env.kijiSystem.getSchemaId(cellSchemaContext.env.instanceURI, avroSchema)
    if (maybeUidForSchemaClass.isEmpty) {
      // Nothing to do; this schema is not registered in the schema table.
      return cellSchema
    }

    val uidForSchemaClass: Long = maybeUidForSchemaClass.get

    // Drop from the main reader/writer/etc lists.
    dropAvroFromCellSchema(avroSchema, cellSchema, cellSchemaContext.schemaUsageFlags,
        cellSchemaContext.env)

    return cellSchema
  }

}
