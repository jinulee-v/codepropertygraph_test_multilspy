package io.shiftleft.codepropertygraph.schema

import flatgraph.schema._

object Annotation extends SchemaBase {
  def docIndex: Int = 20

  override def providedByFrontend: Boolean = true

  override def description: String =
    """
      |Java Annotation related CPG definitions.
      |""".stripMargin

  def apply(
    builder: SchemaBuilder,
    base: Base.Schema,
    methodSchema: Method.Schema,
    typeSchema: Type.Schema,
    ast: Ast.Schema,
    shortcuts: Shortcuts.Schema
  ) =
    new Schema(builder, base, methodSchema, typeSchema, ast, shortcuts)

  class Schema(
    builder: SchemaBuilder,
    base: Base.Schema,
    methodSchema: Method.Schema,
    typeSchema: Type.Schema,
    astSchema: Ast.Schema,
    shortcuts: Shortcuts.Schema
  ) {

    import base._
    import methodSchema._
    import typeSchema._
    import astSchema._
    import shortcuts._

    implicit private val schemaInfo: SchemaInfo = SchemaInfo.forClass(getClass)
    val annotation: NodeType = builder
      .addNodeType(
        name = "ANNOTATION",
        comment = """A method annotation.
          |The semantics of the FULL_NAME property on this node differ from the usual FULL_NAME
          |semantics in the sense that FULL_NAME describes the represented annotation class/interface
          |itself and not the ANNOTATION node.
          |""".stripMargin
      )
      .protoId(ProtoIds.Annotation)
      .addProperties(name, fullName)
      .extendz(expression)

    val annotationParameterAssign: NodeType = builder
      .addNodeType(
        name = "ANNOTATION_PARAMETER_ASSIGN",
        comment = "Assignment of annotation argument to annotation parameter"
      )
      .protoId(ProtoIds.AnnotationParameterAssign)
      .extendz(astNode)

    val annotationParameter: NodeType = builder
      .addNodeType(name = "ANNOTATION_PARAMETER", comment = "Formal annotation parameter")
      .protoId(ProtoIds.AnnotationParameter)
      .extendz(astNode)

    val annotationLiteral: NodeType = builder
      .addNodeType(name = "ANNOTATION_LITERAL", comment = "A literal value assigned to an ANNOTATION_PARAMETER")
      .protoId(ProtoIds.AnnotationLiteral)
      .addProperties(name)
      .extendz(expression)

    val arrayInitializer: NodeType = builder
      .addNodeType(name = "ARRAY_INITIALIZER", comment = "Initialization construct for arrays")
      .protoId(ProtoIds.ArrayInitializer)
      .extendz(astNode)

    arrayInitializer
      .extendz(expression)

    annotation
      .addOutEdge(edge = ast, inNode = annotationParameterAssign)

    annotationParameterAssign
      .addOutEdge(edge = ast, inNode = annotationParameter)
      .addOutEdge(edge = ast, inNode = arrayInitializer)
      .addOutEdge(edge = ast, inNode = annotationLiteral)
      .addOutEdge(edge = ast, inNode = annotation)

    arrayInitializer
      .addOutEdge(edge = ast, inNode = literal)
      .addOutEdge(edge = evalType, inNode = tpe)

    literal
      .addOutEdge(edge = ast, inNode = annotation)

    identifier
      .addOutEdge(edge = ast, inNode = annotation)

    method
      .addOutEdge(edge = ast, inNode = annotation)

    methodParameterIn
      .addOutEdge(edge = ast, inNode = annotation)

    methodRef
      .addOutEdge(edge = ast, inNode = annotation)

    typeDecl
      .addOutEdge(edge = ast, inNode = annotation)

    member
      .addOutEdge(edge = ast, inNode = annotation)

    unknown
      .addOutEdge(edge = ast, inNode = annotation)
  }
}
