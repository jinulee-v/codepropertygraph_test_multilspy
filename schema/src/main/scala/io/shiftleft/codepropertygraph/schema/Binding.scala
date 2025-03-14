package io.shiftleft.codepropertygraph.schema

import flatgraph.schema.{EdgeType, NodeType, SchemaBuilder, SchemaInfo}

object Binding extends SchemaBase {

  def apply(
    builder: SchemaBuilder,
    base: Base.Schema,
    typeSchema: Type.Schema,
    methodSchema: Method.Schema,
    callGraphSchema: CallGraph.Schema
  ) =
    new Schema(builder, base, typeSchema, methodSchema, callGraphSchema)

  override def docIndex: Int = 19

  override def description: String =
    """
      |We use the concept of "bindings" to support resolving of
      |(method-name, signature) pairs at type declarations (`TYPE_DECL`). For each
      |pair that we can resolve, we create a `BINDING` node that is connected the
      |the type declaration via an incoming `BINDS` edge. The `BINDING` node is
      |connected to the method it resolves to via an outgoing `REF` edge.
      |""".stripMargin

  class Schema(
    builder: SchemaBuilder,
    base: Base.Schema,
    typeDeclSchema: Type.Schema,
    methodSchema: Method.Schema,
    callGraphSchema: CallGraph.Schema
  ) {
    import base._
    import typeDeclSchema._
    import methodSchema._
    import callGraphSchema._

    implicit private val schemaInfo: SchemaInfo = SchemaInfo.forClass(getClass)

    val binding: NodeType = builder
      .addNodeType(
        name = "BINDING",
        comment = """`BINDING` nodes represent name-signature pairs that can be resolved at a
            |type declaration (`TYPE_DECL`). They are connected to `TYPE_DECL` nodes via
            |incoming `BINDS` edges. The bound method is either associated with an outgoing
            |`REF` edge to a `METHOD` or with the `METHOD_FULL_NAME` property. The `REF` edge
            |if present has priority.
            |""".stripMargin
      )
      .protoId(ProtoIds.Binding)
      .addProperties(name, signature, methodFullName)

    val binds = builder
      .addEdgeType(
        name = "BINDS",
        comment = """This edge connects a type declaration (`TYPE_DECL`) with a binding node (`BINDING`) and
            |indicates that the type declaration has the binding represented by the binding node, in
            |other words, there is a (name, signature) pair that can be resolved for the type
            |declaration as stored in the binding node.
            |""".stripMargin
      )
      .protoId(ProtoIds.Binds)

    typeDecl.addOutEdge(
      edge = binds,
      inNode = binding,
      cardinalityIn = EdgeType.Cardinality.One,
      stepNameIn = "bindingTypeDecl"
    )

    binding.addOutEdge(
      edge = ref,
      inNode = method,
      cardinalityOut = EdgeType.Cardinality.One,
      stepNameOut = "boundMethod"
    )

  }

}
