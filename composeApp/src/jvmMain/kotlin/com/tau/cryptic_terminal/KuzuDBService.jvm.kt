package com.tau.cryptic_terminal

import com.kuzudb.Connection
import com.kuzudb.Database
import com.kuzudb.Value
import com.kuzudb.QueryResult
import com.kuzudb.ValueNodeUtil
import com.kuzudb.ValueRelUtil
import com.kuzudb.ValueRecursiveRelUtil
import com.kuzudb.DataTypeID as KuzuTypeId
import java.nio.file.Files
import java.nio.file.Paths
import java.math.BigInteger
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toKotlinUuid


 class KuzuDBService {
    private var db: Database? = null
    private var conn: Connection? = null

     /**
      * Initialize with a directory path
      */
     fun initialize(directoryPath: String) {
        try {
            val dbDirectory = Paths.get(directoryPath)
            if (!Files.exists(dbDirectory)) {
                Files.createDirectories(dbDirectory)
            }
            val dbPath = dbDirectory.resolve("database.kuzudb").toString()
            db = Database(dbPath)
            conn = Connection(db)
            println("KuzuDB initialized successfully at: $dbPath")
        } catch (e: Exception) {
            println("Failed to initialize KuzuDB: ${e.message}")
            e.printStackTrace()
        }
    }

     /**
      * Initialize in memory database
      */
     fun initialize() {
         try {
             db = Database(":memory:")
             conn = Connection(db)
             println("In-Memory KuzuDB initialized successfully.")
         } catch (e: Exception) {
             println("Failed to initialize KuzuDB: ${e.message}")
             e.printStackTrace()
         }
     }

     fun close() {
        try {
            conn?.close()
            db?.close()
            println("KuzuDB connection closed.")
        } catch (e: Exception) {
            println("Failed to close KuzuDB connection: ${e.message}")
        }
    }

     fun executeQuery(query: String): QueryResult? {
        var result: QueryResult? = null
         try {
            result =  conn?.query(query)
        } catch (e: Exception) {
            println(e.message)
        }
         return result
    }

    // --- Private Helper Functions ---

//    private fun executeQuery(query: String, description: String, params: Map<String, Value> = emptyMap()): Boolean {
//        return try {
//            println("Executing query: $query")
//            val preparedStatement = conn?.prepare(query)
//            conn?.execute(preparedStatement, params)
//            println("Successfully executed query: $description")
//            true
//        } catch (e: Exception) {
//            println("Failed to execute query '$description': ${e.message}")
//            e.printStackTrace()
//            false
//        }
//    }

//    private fun executeQueryAndParseResults(query: String, description: String, params: Map<String, Value> = emptyMap()): List<Map<String, Any?>> {
//        val results = mutableListOf<Map<String, Any?>>()
//        try {
//            println("Executing query: $query")
//            val queryResult = if (params.isEmpty()) {
//                conn?.query(query)
//            } else {
//                val preparedStatement = conn?.prepare(query)
//                conn?.execute(preparedStatement, params)
//            }
//            queryResult?.let {
//                while (it.hasNext()) {
//                    val row = it.next
//                    val rowMap = mutableMapOf<String, Any?>()
//                    for (i in 0 until it.numColumns) {
//                        val columnName = it.getColumnName(i)
//                        val value = row.getValue(i)
//                        rowMap[columnName] = convertKuzuValueToJavaType(value)
//                    }
//                    results.add(rowMap)
//                }
//                it.close()
//            }
//            println("Successfully executed query and parsed results: $description")
//        } catch (e: Exception) {
//            println("Failed to execute query '$description': ${e.message}")
//            e.printStackTrace()
//        }
//        return results
//    }

//    private fun convertKuzuValueToJavaType(kuzuValue: Value?): Any? {
//        if (kuzuValue == null || kuzuValue.isNull) {
//            return null
//        }
//        return when (val typeId = kuzuValue.dataType.id) {
//            KuzuTypeId.NODE -> {
//                val propertyMap = mutableMapOf<String, Any?>()
//                propertyMap["_id"] = ValueNodeUtil.getID(kuzuValue).toString()
//                propertyMap["_label"] = ValueNodeUtil.getLabelName(kuzuValue)
//                for (i in 0 until ValueNodeUtil.getPropertySize(kuzuValue)) {
//                    val name = ValueNodeUtil.getPropertyNameAt(kuzuValue, i)
//                    val value = ValueNodeUtil.getPropertyValueAt(kuzuValue, i)
//                    propertyMap[name] = convertKuzuValueToJavaType(value)
//                }
//                propertyMap
//            }
//            KuzuTypeId.REL -> {
//                val propertyMap = mutableMapOf<String, Any?>()
//                propertyMap["_id"] = ValueRelUtil.getID(kuzuValue).toString()
//                propertyMap["_label"] = ValueRelUtil.getLabelName(kuzuValue)
//                propertyMap["_src"] = ValueRelUtil.getSrcID(kuzuValue).toString()
//                propertyMap["_dst"] = ValueRelUtil.getDstID(kuzuValue).toString()
//                for (i in 0 until ValueRelUtil.getPropertySize(kuzuValue)) {
//                    val name = ValueRelUtil.getPropertyNameAt(kuzuValue, i)
//                    val value = ValueRelUtil.getPropertyValueAt(kuzuValue, i)
//                    propertyMap[name] = convertKuzuValueToJavaType(value)
//                }
//                propertyMap
//            }
//            KuzuTypeId.RECURSIVE_REL -> {
//                val recursiveRelMap = mutableMapOf<String, Any?>()
//                val nodes = ValueRecursiveRelUtil.getNodeList(kuzuValue)
//                val rels = ValueRecursiveRelUtil.getRelList(kuzuValue)
//                recursiveRelMap["_nodes"] = convertKuzuValueToJavaType(nodes)
//                recursiveRelMap["_rels"] = convertKuzuValueToJavaType(rels)
//                recursiveRelMap
//            }
//            KuzuTypeId.BOOL -> kuzuValue.getValue<Boolean>()
//            KuzuTypeId.INT64 -> kuzuValue.getValue<Long>()
//            KuzuTypeId.INT32 -> kuzuValue.getValue<Int>()
//            KuzuTypeId.INT16 -> kuzuValue.getValue<Short>()
//            KuzuTypeId.INT128 -> kuzuValue.getValue<BigInteger>().toString()
//            KuzuTypeId.DOUBLE -> kuzuValue.getValue<Double>()
//            KuzuTypeId.FLOAT -> kuzuValue.getValue<Float>()
//            KuzuTypeId.STRING -> kuzuValue.getValue<String>()
//            KuzuTypeId.UUID -> kuzuValue.toString()
//            KuzuTypeId.LIST -> kuzuValue.getValue<List<*>>().map { convertKuzuValueToJavaType(it as Value) }
//            KuzuTypeId.MAP -> {
//                // Assuming map keys are strings
//                val originalMap = kuzuValue.getValue<Map<*,*>>()
//                originalMap.mapValues { convertKuzuValueToJavaType(it.value as Value) }
//            }
//            KuzuTypeId.STRUCT -> {
//                val structFields = kuzuValue.dataType.structFields
//                val structMap = mutableMapOf<String, Any?>()
//                for(i in structFields.indices) {
//                    structMap[structFields[i].name] = convertKuzuValueToJavaType(kuzuValue.getValue<List<Value>>()[i])
//                }
//                structMap
//            }
//            else -> {
//                println("Unhandled Kuzu data type in conversion: $typeId")
//                kuzuValue.toString()
//            }
//        }
//    }
}