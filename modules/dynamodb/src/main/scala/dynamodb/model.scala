/*
 * Copyright 2018 OVO Energy
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

package com.ovoenergy.comms.aws
package dynamodb

import scala.reflect.macros.whitebox

import scodec.bits._

import cats.implicits._

object model {

  case class TableName(value: String)

  case class AttributeName(value: String)

  sealed trait AttributeValue
  object AttributeValue {
    case object NULL extends AttributeValue
    case class S(value: String) extends AttributeValue
    case class N(value: String) extends AttributeValue
    case class B(value: ByteVector) extends AttributeValue
    case class BOOL(value: Boolean) extends AttributeValue
    case class M(values: Map[AttributeName, AttributeValue])
        extends AttributeValue
    case class L(values: List[AttributeValue]) extends AttributeValue
    case class SS(values: Set[String]) extends AttributeValue
    case class NS(values: Set[String]) extends AttributeValue
    case class BS(values: Set[ByteVector]) extends AttributeValue

    val `null`: AttributeValue = NULL

    def m(values: (AttributeName, AttributeValue)*): AttributeValue =
      AttributeValue.M(values.toMap)
    def m(values: Map[AttributeName, AttributeValue]): AttributeValue =
      AttributeValue.M(values)

    def s(value: String): AttributeValue = AttributeValue.S(value)

    def ss(values: Set[String]): AttributeValue = AttributeValue.SS(values)
    def ss(values: String*): AttributeValue = AttributeValue.SS(values.toSet)

    def n(value: String): AttributeValue = AttributeValue.N(value)
    def n(value: Int): AttributeValue = AttributeValue.N(value.toString)
    def n(value: Long): AttributeValue = AttributeValue.N(value.toString)
    def n(value: Double): AttributeValue = AttributeValue.N(value.toString)
    def n(value: Float): AttributeValue = AttributeValue.N(value.toString)
    def n(value: Short): AttributeValue = AttributeValue.N(value.toString)
    def n(value: Byte): AttributeValue = AttributeValue.N(value.toString)

    def b(value: ByteVector): AttributeValue = AttributeValue.B(value)
    def b(value: Array[Byte]): AttributeValue =
      AttributeValue.B(ByteVector(value))
    def b(value: Seq[Byte]): AttributeValue =
      AttributeValue.B(ByteVector(value))

    def bool(value: Boolean): AttributeValue = AttributeValue.BOOL(value)

    def l(values: AttributeValue*): AttributeValue =
      AttributeValue.L(values.toList)
    def l(values: List[AttributeValue]): AttributeValue =
      AttributeValue.L(values.toList)
  }

  sealed trait ReturnValues
  object ReturnValues {

    /**
      * nothing is returned
      */
    case object None extends ReturnValues

    /**
      * the content of the old item is returned
      */
    case object AllOld extends ReturnValues

    case object UpdatedOld extends ReturnValues
    case object AllNew extends ReturnValues
    case object UpdatedNew extends ReturnValues

  }

  case class ConditionExpression(value: String)

  case class ProjectionExpression(value: String)

  case class UpdateExpression(value: String)

  // TODO Macro to instantiate it from static string
  case class ExpressionAlias private (value: String)

  object ExpressionAlias {

    class Macros(val c: whitebox.Context) {
      import c.universe._

      def literal(s: c.Expr[String]): Tree =
        s.tree match {
          case Literal(Constant(s: String)) =>
            ExpressionAlias
              .fromString(s)
              .fold(
                e => c.abort(c.enclosingPosition, e),
                _ =>
                  q"_root_.com.ovoenergy.comms.aws.dynamodb.model.ExpressionAlias.unsafeFromString($s)"
              )
          case _ =>
            c.abort(
              c.enclosingPosition,
              s"This method uses a macro to verify that a String literal is a valid ExpressionAlias. Use ExpressionAlias.fromString if you have a dynamic String that you want to parse."
            )
        }
    }

    def apply(s: String): ExpressionAlias = macro ExpressionAlias.Macros.literal

    def fromString(str: String): Either[String, ExpressionAlias] =
      if (str.headOption.contains('#')) {
        new ExpressionAlias(str).asRight
      } else {
        s"Valid expression alias must start with '#'. Invalid placeholder: '$str'".asLeft
      }

    def unsafeFromString(str: String): ExpressionAlias =
      fromString(str).getOrElse(
        throw new IllegalArgumentException(
          s"$str is not a valid expression alias")
      )
  }

  // TODO Macro to instantiate it from static string
  case class ExpressionPlaceholder private (value: String)

  object ExpressionPlaceholder {

    class Macros(val c: whitebox.Context) {
      import c.universe._

      def literal(s: c.Expr[String]): Tree =
        s.tree match {
          case Literal(Constant(s: String)) =>
            ExpressionPlaceholder
              .fromString(s)
              .fold(
                e => c.abort(c.enclosingPosition, e),
                _ =>
                  q"_root_.com.ovoenergy.comms.aws.dynamodb.model.ExpressionPlaceholder.unsafeFromString($s)"
              )
          case _ =>
            c.abort(
              c.enclosingPosition,
              s"This method uses a macro to verify that a String literal is a valid ExpressionPlaceholder. Use ExpressionPlaceholder.fromString if you have a dynamic String that you want to parse."
            )
        }
    }

    def apply(s: String): ExpressionPlaceholder =
      macro ExpressionPlaceholder.Macros.literal

    def fromString(str: String): Either[String, ExpressionPlaceholder] =
      if (str.headOption.contains(':')) {
        new ExpressionPlaceholder(str).asRight
      } else {
        s"Valid expression placeholdert must start with ':'. Invalid placeholder: '$str'".asLeft
      }

    def unsafeFromString(str: String): ExpressionPlaceholder =
      fromString(str).fold(e => throw new IllegalArgumentException(e), identity)

  }

  case class PutItemRequest(
      tableName: TableName,
      item: AttributeValue.M,
      conditionExpression: Option[ConditionExpression] = None,
      expressionAttributeNames: Map[ExpressionAlias, AttributeName] = Map.empty,
      expressionAttributeValues: Map[ExpressionPlaceholder, AttributeValue] =
        Map.empty,
      returnValues: ReturnValues = ReturnValues.None,
  )

  case class PutItemResponse(attributes: Option[AttributeValue.M])

  case class GetItemRequest(
      tableName: TableName,
      key: AttributeValue.M,
      consistent: Boolean = false,
      projectionExpression: Option[ProjectionExpression] = None,
      expressionAttributeNames: Map[ExpressionAlias, AttributeName] = Map.empty
  )

  case class GetItemResponse(
      item: Option[AttributeValue.M]
  )

  case class DeleteItemRequest(
      tableName: TableName,
      key: AttributeValue.M,
      conditionExpression: Option[ConditionExpression] = None,
      expressionAttributeNames: Map[ExpressionAlias, AttributeName] = Map.empty,
      expressionAttributeValues: Map[ExpressionPlaceholder, AttributeValue] =
        Map.empty,
      returnValues: ReturnValues = ReturnValues.None
  )

  case class DeleteItemResponse(
      attributes: Option[AttributeValue.M]
  )

  case class UpdateItemRequest(
      tableName: TableName,
      key: AttributeValue.M,
      updateExpression: UpdateExpression,
      expressionAttributeNames: Map[ExpressionAlias, AttributeName] = Map.empty,
      expressionAttributeValues: Map[ExpressionPlaceholder, AttributeValue] =
        Map.empty,
      conditionExpression: Option[ConditionExpression] = None,
      returnValues: ReturnValues = ReturnValues.None
  )

  case class UpdateItemResponse(
      attributes: Option[AttributeValue.M]
  )

  // TODO Model all the DynamoDb errors
  case class DynamoDbError(message: String, retriable: Boolean = false)
      extends Exception(message)

  trait AwsOp[Req, Res] {
    def target: String
  }

  object AwsOp {

    def instance[Req, Res](target_ : String): AwsOp[Req, Res] =
      new AwsOp[Req, Res] {
        val target = target_
      }

    implicit val put: AwsOp[PutItemRequest, PutItemResponse] =
      instance("DynamoDB_20120810.PutItem")

    implicit val get: AwsOp[GetItemRequest, GetItemResponse] =
      instance("DynamoDB_20120810.GetItem")

    implicit val delete: AwsOp[DeleteItemRequest, DeleteItemResponse] =
      instance("DynamoDB_20120810.DeleteItem")

    implicit val update: AwsOp[UpdateItemRequest, UpdateItemResponse] =
      instance("DynamoDB_20120810.UpdateItem")
  }
}
