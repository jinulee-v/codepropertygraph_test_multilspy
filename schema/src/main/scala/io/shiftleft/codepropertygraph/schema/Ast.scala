package io.shiftleft.codepropertygraph.schema

import io.shiftleft.codepropertygraph.schema.CpgSchema.PropertyDefaults
import flatgraph.schema.EdgeType.Cardinality
import flatgraph.schema.Property.ValueType
import flatgraph.schema.{Constant, NodeType, SchemaBuilder, SchemaInfo}

object Ast extends SchemaBase {

  def docIndex: Int                        = 7
  override def providedByFrontend: Boolean = true

  override def description: String =
    """
      |The Abstract Syntax Tree (AST) Layer provides syntax trees for all compilation units.
      |All nodes of the tree inherit from the same base class (`AST_NODE`) and are connected
      |to their child nodes via outgoing `AST` edges.
      |
      |Syntax trees are typed, that is, when possible, types for all expressions are stored
      |in the tree. Moreover, common control structure types are defined in the specification,
      |making it possible to translate trees into corresponding control flow graphs if only
      |these common control structure types are used, possibly by desugaring on the side of
      |the language frontend. For cases where this is not an option,
      |the AST specification provides means of storing language-dependent information in the
      |AST that can be interpreted by language-dependent control flow construction passes.
      |
      |This layer MUST be created by the frontend.
      |
      |""".stripMargin

  def apply(
    builder: SchemaBuilder,
    base: Base.Schema,
    namespaces: Namespace.Schema,
    methodSchema: Method.Schema,
    typeSchema: Type.Schema,
    fs: FileSystem.Schema
  ) =
    new Schema(builder, base, namespaces, methodSchema, typeSchema, fs)

  class Schema(
    builder: SchemaBuilder,
    base: Base.Schema,
    namespaces: Namespace.Schema,
    methodSchema: Method.Schema,
    typeSchema: Type.Schema,
    fs: FileSystem.Schema
  ) {
    implicit private val schemaInfo: SchemaInfo = SchemaInfo.forClass(getClass)
    import base._
    import fs._
    import methodSchema._
    import namespaces._
    import typeSchema._

    // Base types

    val order = builder
      .addProperty(
        name = "ORDER",
        valueType = ValueType.Int,
        comment = """This integer indicates the position of the node among
            |its siblings in the AST. The left-most child has an
            |order of 0.
            |""".stripMargin
      )
      .mandatory(-1)
      .protoId(ProtoIds.Order)

    val astNode = builder
      .addNodeBaseType(
        name = "AST_NODE",
        comment = """This is the base type for all nodes of the abstract syntax tree (AST). An AST
                    |node has a `CODE` and an `ORDER` field. The `CODE` field contains the
                    |code (verbatim) represented by the AST node. The `ORDER` field contains the
                    |nodes position among its siblings, encoded as an integer where the left most
                    |sibling has the position `0`.
                    |
                    |AST nodes contain optional `LINE_NUMBER` and `COLUMN_NUMBER` fields. For
                    |source-based frontends, these fields contain the start line number and
                    |start column number of the code represented by the node.
                    |For machine-code-based and bytecode-based frontends, `LINE_NUMBER` contains
                    |the address at which the code starts while `COLUMN_NUMBER` is undefined.
                    |
                    |The optional `OFFSET` and `OFFSET_END` specify the start
                    |and exclusive end position of the code belonging to a node within the corresponding
                    |`FILE` nodes `CONTENT` property.
                    |""".stripMargin
      )
      .addProperties(order, code)
      .addProperties(lineNumber, columnNumber, offset, offsetEnd)

    // The following nodes from other schemas are AST nodes

    method.extendz(astNode)
    methodParameterIn.extendz(astNode)
    methodParameterOut.extendz(astNode)

    typeDecl.extendz(astNode)
    member.extendz(astNode)
    typeParameter.extendz(astNode)
    typeArgument.extendz(astNode)

    file.extendz(astNode)
    namespaceBlock.extendz(astNode)

    // Type-related nodes that are part of the AST

    val block: NodeType = builder
      .addNodeType(
        name = "BLOCK",
        comment = """This node represents a compound statement. Compound statements are used in many languages to allow
                    |grouping a sequence of statements. For example, in C and Java, compound statements
                    |are statements enclosed by curly braces. Function/Method bodies are compound
                    |statements. We do not use the term "compound statement" because "statement" would
                    |imply that the block does not yield a value upon evaluation, that is, that it is
                    |not an expression. This is true in languages such as C and Java, but not for languages
                    |such as Scala where the value of the block is given by that of the last expression it
                    |contains. In fact, the Scala grammar uses the term "BlockExpr" (short for
                    |"block expression") to describe what in the CPG we call "Block".
                    |""".stripMargin
      )
      .protoId(ProtoIds.Block)
      .addProperties(typeFullName)

    val literal: NodeType = builder
      .addNodeType(
        name = "LITERAL",
        comment = """This node represents a literal such as an integer or string constant. Literals
            |are symbols included in the code in verbatim form and which are immutable.
            |The `TYPE_FULL_NAME` field stores the literal's fully-qualified type name,
            |e.g., `java.lang.Integer`.
            |""".stripMargin
      )
      .protoId(ProtoIds.Literal)
      .addProperties(typeFullName)
      .primaryKey(code)

    val local: NodeType = builder
      .addNodeType(
        name = "LOCAL",
        comment = """This node represents a local variable. Its fully qualified type name is stored
            |in the `TYPE_FULL_NAME` field and its name in the `NAME` field. The `CODE` field
            |contains the entire local variable declaration without initialization, e.g., for
            |`int x = 10;`, it contains `int x`.
            |""".stripMargin
      )
      .protoId(ProtoIds.Local)
      .addProperties(typeFullName)
      .extendz(declaration, astNode)
      .primaryKey(name)

    val identifier: NodeType = builder
      .addNodeType(
        name = "IDENTIFIER",
        comment = """This node represents an identifier as used when referring to a variable by name.
            |It holds the identifier's name in the `NAME` field and its fully-qualified type
            |name in `TYPE_FULL_NAME`.
            |""".stripMargin
      )
      .protoId(ProtoIds.Identifier)
      .addProperties(typeFullName, name)
      .primaryKey(name)

    val canonicalName = builder
      .addProperty(
        name = "CANONICAL_NAME",
        valueType = ValueType.String,
        comment = """This field holds the canonical name of a `FIELD_IDENTIFIER`. It is typically
                    |identical to the CODE field, but canonicalized according to source language
                    |semantics. Human readable names are preferable. `FIELD_IDENTIFIER` nodes must
                    |share identical `CANONICAL_NAME` if and
                    |only if they alias, e.g., in C-style unions (if the aliasing relationship is
                    |unknown or there are partial overlaps, then one must make a reasonable guess,
                    |and trade off between false negatives and false positives).
                    |""".stripMargin
      )
      .mandatory(PropertyDefaults.String)
      .protoId(ProtoIds.CanonicalName)

    val fieldIdentifier: NodeType = builder
      .addNodeType(
        name = "FIELD_IDENTIFIER",
        comment = """This node represents the field accessed in a field access, e.g., in
                    |`a.b`, it represents `b`. The field name as it occurs in the code is
                    |stored in the `CODE` field. This may mean that the `CODE` field holds
                    |an expression. The `CANONICAL_NAME` field MAY contain the same value is
                    |the `CODE` field but SHOULD contain the normalized name that results
                    |from evaluating `CODE` as an expression if such an evaluation is
                    |possible for the language frontend. The objective is to store an identifier
                    |in `CANONICAL_NAME` that is the same for two nodes iff they refer to the
                    |same field, regardless of whether they use the same expression to reference
                    |it.
                    |""".stripMargin
      )
      .protoId(ProtoIds.FieldIdentifier)
      .addProperties(canonicalName)

    val modifierType = builder
      .addProperty(
        name = "MODIFIER_TYPE",
        valueType = ValueType.String,
        comment = """The modifier type is a free-form string. The following are known modifier types:
            |`STATIC`, `PUBLIC`, `PROTECTED`, `PRIVATE`, `ABSTRACT`, `NATIVE`, `CONSTRUCTOR`, `VIRTUAL`.
            |""".stripMargin
      )
      .mandatory(PropertyDefaults.String)
      .protoId(ProtoIds.ModifierType)

    val modifierTypes = builder.addConstants(
      category = "ModifierTypes",
      Constant(name = "STATIC", value = "STATIC", valueType = ValueType.String, comment = "The static modifier")
        .protoId(ProtoIds.Static),
      Constant(name = "PUBLIC", value = "PUBLIC", valueType = ValueType.String, comment = "The public modifier")
        .protoId(ProtoIds.Public),
      Constant(
        name = "PROTECTED",
        value = "PROTECTED",
        valueType = ValueType.String,
        comment = "The protected modifier"
      ).protoId(ProtoIds.Protected),
      Constant(name = "PRIVATE", value = "PRIVATE", valueType = ValueType.String, comment = "The private modifier")
        .protoId(ProtoIds.Private),
      Constant(name = "ABSTRACT", value = "ABSTRACT", valueType = ValueType.String, comment = "The abstract modifier")
        .protoId(ProtoIds.Abstract),
      Constant(name = "NATIVE", value = "NATIVE", valueType = ValueType.String, comment = "The native modifier")
        .protoId(ProtoIds.Native),
      Constant(
        name = "CONSTRUCTOR",
        value = "CONSTRUCTOR",
        valueType = ValueType.String,
        comment = "The constructor modifier"
      ).protoId(ProtoIds.Constructor),
      Constant(name = "VIRTUAL", value = "VIRTUAL", valueType = ValueType.String, comment = "The virtual modifier")
        .protoId(ProtoIds.Virtual),
      Constant(name = "INTERNAL", value = "INTERNAL", valueType = ValueType.String, comment = "The internal modifier")
        .protoId(ProtoIds.Internal),
      Constant(name = "FINAL", value = "FINAL", valueType = ValueType.String, comment = "The final modifier")
        .protoId(ProtoIds.Final),
      Constant(name = "READONLY", value = "READONLY", valueType = ValueType.String, comment = "The readonly modifier")
        .protoId(ProtoIds.ReadOnly),
      Constant(
        name = "MODULE",
        value = "MODULE",
        valueType = ValueType.String,
        comment =
          "Indicate that a method defines a module in the sense e.g. a python module does with the creation of a module object"
      )
        .protoId(ProtoIds.Module),
      Constant(
        name = "LAMBDA",
        value = "LAMBDA",
        valueType = ValueType.String,
        comment = "Indicate that a method is an anonymous function, lambda, or closure"
      )
        .protoId(ProtoIds.Lambda)
    )

    val modifier: NodeType = builder
      .addNodeType(
        name = "MODIFIER",
        comment = """This field represents a (language-dependent) modifier such as `static`, `private`
            |or `public`. Unlike most other AST nodes, it is NOT an expression, that is, it
            |cannot be evaluated and cannot be passed as an argument in function calls.
            |""".stripMargin
      )
      .protoId(ProtoIds.Modifier)
      .addProperties(modifierType)
      .extendz(astNode)

    val jumpTarget: NodeType = builder
      .addNodeType(
        name = "JUMP_TARGET",
        comment = """A jump target is any location in the code that has been specifically marked
            |as the target of a jump, e.g., via a label. The `NAME` field holds the name of
            |the label while the `PARSER_TYPE_NAME` field holds the name of language construct
            |that this jump target is created from, e.g., "Label".
            |""".stripMargin
      )
      .protoId(ProtoIds.JumpTarget)
      .addProperties(name, parserTypeName)
      .extendz(astNode)

    val jumpLabel: NodeType = builder
      .addNodeType(
        name = "JUMP_LABEL",
        comment = """A jump label specifies the label and thus the JUMP_TARGET of control structures
                    |BREAK and CONTINUE. The `NAME` field holds the name of the label while the
                    |`PARSER_TYPE_NAME` field holds the name of language construct that this jump
                    |label is created from, e.g., "Label".
                    |""".stripMargin
      )
      .protoId(ProtoIds.JumpLabel)
      .addProperties(name, parserTypeName)
      .extendz(astNode)

    val methodRef: NodeType = builder
      .addNodeType(
        name = "METHOD_REF",
        comment = """This node represents a reference to a method/function/procedure as it
            |appears when a method is passed as an argument in a call. The `METHOD_FULL_NAME`
            |field holds the fully-qualified name of the referenced method and the
            |`TYPE_FULL_NAME` holds its fully-qualified type name.
            |""".stripMargin
      )
      .protoId(ProtoIds.MethodRef)
      .addProperties(typeFullName)

    val typeRef: NodeType = builder
      .addNodeType(name = "TYPE_REF", comment = "Reference to a type/class")
      .protoId(ProtoIds.TypeRef)
      .addProperties(typeFullName)

    val ret: NodeType = builder
      .addNodeType(
        name = "RETURN",
        comment = """This node represents a return instruction, e.g., `return x`. Note that it does
            |NOT represent a formal return parameter as formal return parameters are
            |represented via `METHOD_RETURN` nodes.
            |""".stripMargin
      )
      .protoId(ProtoIds.Return)
      .starterName("ret")
      .primaryKey(code)

    val controlStructureType = builder
      .addProperty(
        name = "CONTROL_STRUCTURE_TYPE",
        valueType = ValueType.String,
        comment = """The `CONTROL_STRUCTURE_TYPE` field indicates which kind of control structure
            |a `CONTROL_STRUCTURE` node represents. The available types are the following:
            | BREAK, CONTINUE, DO, WHILE, FOR, GOTO, IF, ELSE, TRY, THROW and SWITCH.
            |""".stripMargin
      )
      .mandatory(PropertyDefaults.String)
      .protoId(ProtoIds.ControlStructureType)

    val controlStructureTypes = builder.addConstants(
      category = "ControlStructureTypes",
      Constant(
        name = "BREAK",
        value = "BREAK",
        valueType = ValueType.String,
        comment = """Represents a break statement. Labeled breaks are expected to have a JUMP_LABEL
            |node AST child with ORDER 1""".stripMargin
      ).protoId(ProtoIds.Break),
      Constant(
        name = "CONTINUE",
        value = "CONTINUE",
        valueType = ValueType.String,
        comment = """Represents a continue statement. Labeled continues are expected to have a JUMP_LABEL
                           |node AST child with ORDER 1""".stripMargin
      ).protoId(ProtoIds.Continue),
      Constant(name = "WHILE", value = "WHILE", valueType = ValueType.String, comment = "Represents a while statement")
        .protoId(ProtoIds.While),
      Constant(name = "DO", value = "DO", valueType = ValueType.String, comment = "Represents a do statement")
        .protoId(ProtoIds.Do),
      Constant(name = "FOR", value = "FOR", valueType = ValueType.String, comment = "Represents a for statement")
        .protoId(ProtoIds.For),
      Constant(name = "GOTO", value = "GOTO", valueType = ValueType.String, comment = "Represents a goto statement")
        .protoId(ProtoIds.Goto),
      Constant(name = "IF", value = "IF", valueType = ValueType.String, comment = "Represents an if statement")
        .protoId(ProtoIds.If),
      Constant(name = "ELSE", value = "ELSE", valueType = ValueType.String, comment = "Represents an else statement")
        .protoId(ProtoIds.Else),
      Constant(
        name = "SWITCH",
        value = "SWITCH",
        valueType = ValueType.String,
        comment = "Represents a switch statement"
      ).protoId(ProtoIds.Switch),
      Constant(name = "TRY", value = "TRY", valueType = ValueType.String, comment = "Represents a try statement")
        .protoId(ProtoIds.Try),
      Constant(name = "THROW", value = "THROW", valueType = ValueType.String, comment = "Represents a throw statement")
        .protoId(ProtoIds.Throw),
      Constant(name = "MATCH", value = "MATCH", valueType = ValueType.String, comment = "Represents a match expression")
        .protoId(ProtoIds.Match),
      Constant(name = "YIELD", value = "YIELD", valueType = ValueType.String, comment = "Represents a yield expression")
        .protoId(ProtoIds.Yield),
      Constant(name = "CATCH", value = "CATCH", valueType = ValueType.String, comment = "Represents a catch clause")
        .protoId(ProtoIds.Catch),
      Constant(
        name = "FINALLY",
        value = "FINALLY",
        valueType = ValueType.String,
        comment = "Represents a finally clause"
      )
        .protoId(ProtoIds.Finally)
    )

    val controlStructure: NodeType = builder
      .addNodeType(
        name = "CONTROL_STRUCTURE",
        comment = """This node represents a control structure as introduced by control structure
            |statements as well as conditional and unconditional jumps. Its type is stored in the
            |`CONTROL_STRUCTURE_TYPE` field to be one of several pre-defined types. These types
            | are used in the construction of the control flow layer, making it possible to
            | generate the control flow layer from the abstract syntax tree layer automatically.
            |
            |In addition to the `CONTROL_STRUCTURE_TYPE` field, the `PARSER_TYPE_NAME` field
            |MAY be used by frontends to store the name of the control structure as emitted by
            |the parser or disassembler, however, the value of this field is not relevant
            |for construction of the control flow layer.
            |""".stripMargin
      )
      .protoId(ProtoIds.ControlStructure)
      .addProperties(parserTypeName, controlStructureType)

    val unknown: NodeType = builder
      .addNodeType(
        name = "UNKNOWN",
        comment = """Any AST node that the frontend would like to include in the AST but for
            |which no suitable AST node is specified in the CPG specification may be
            |included using a node of type `UNKNOWN`.
            |""".stripMargin
      )
      .protoId(ProtoIds.Unknown)
      .addProperties(parserTypeName, typeFullName)

    // Edge types

    val ast = builder
      .addEdgeType(name = "AST", comment = "This edge connects a parent node to its child in the syntax tree.")
      .protoId(ProtoIds.Ast)

    val condition = builder
      .addEdgeType(
        name = "CONDITION",
        comment = "The edge connects control structure nodes to the expressions that holds their conditions."
      )
      .protoId(ProtoIds.Condition)

    file.addOutEdge(edge = ast, inNode = namespaceBlock, cardinalityIn = Cardinality.ZeroOrOne)

    member.addOutEdge(edge = ast, inNode = modifier)
    tpe.addOutEdge(edge = ast, inNode = typeArgument)

    typeDecl
      .addOutEdge(edge = ast, inNode = typeParameter)
      .addOutEdge(
        edge = ast,
        inNode = member,
        cardinalityIn = Cardinality.One,
        stepNameIn = "typeDecl",
        stepNameInDoc = "The type declaration this member is defined in"
      )
      .addOutEdge(edge = ast, inNode = modifier, cardinalityIn = Cardinality.One)

    method
      .addOutEdge(
        edge = ast,
        inNode = methodReturn,
        cardinalityOut = Cardinality.One,
        cardinalityIn = Cardinality.One,
        stepNameOut = "methodReturn",
        stepNameOutDoc = "Formal return parameters"
      )
      .addOutEdge(
        edge = ast,
        inNode = methodParameterIn,
        cardinalityIn = Cardinality.One,
        stepNameOut = "parameter",
        stepNameOutDoc = "Parameters of the method",
        stepNameIn = "method",
        stepNameInDoc = "Traverse to method associated with this formal parameter"
      )
      .addOutEdge(edge = ast, inNode = modifier, cardinalityIn = Cardinality.One)
      .addOutEdge(
        edge = ast,
        inNode = block,
        cardinalityOut = Cardinality.One,
        cardinalityIn = Cardinality.One,
        stepNameOut = "block",
        stepNameOutDoc = "Root of the abstract syntax tree"
      )
      .addOutEdge(edge = ast, inNode = typeParameter, cardinalityIn = Cardinality.One)

    ret
      .addOutEdge(edge = ast, inNode = identifier)
      .addOutEdge(edge = ast, inNode = literal)
      .addOutEdge(edge = ast, inNode = methodRef)
      .addOutEdge(edge = ast, inNode = typeRef)
      .addOutEdge(edge = ast, inNode = ret)
      .addOutEdge(edge = ast, inNode = block)
      .addOutEdge(edge = ast, inNode = unknown)
      .addOutEdge(edge = ast, inNode = jumpTarget)
      .addOutEdge(edge = ast, inNode = controlStructure)

    block
      .addOutEdge(edge = ast, inNode = identifier)
      .addOutEdge(edge = ast, inNode = literal)
      .addOutEdge(edge = ast, inNode = methodRef)
      .addOutEdge(edge = ast, inNode = typeRef)
      .addOutEdge(edge = ast, inNode = ret)
      .addOutEdge(edge = ast, inNode = block, cardinalityIn = Cardinality.One)
      .addOutEdge(
        edge = ast,
        inNode = local,
        stepNameIn = "definingBlock",
        stepNameInDoc = "The block in which local is declared.",
        stepNameOut = "local",
        stepNameOutDoc = "Traverse to locals of this block."
      )
      .addOutEdge(edge = ast, inNode = unknown)
      .addOutEdge(edge = ast, inNode = jumpTarget)
      .addOutEdge(edge = ast, inNode = controlStructure)

    controlStructure
      .addOutEdge(edge = ast, inNode = literal, cardinalityIn = Cardinality.One)
      .addOutEdge(edge = ast, inNode = modifier)
      .addOutEdge(edge = ast, inNode = local)
      .addOutEdge(edge = ast, inNode = identifier, cardinalityIn = Cardinality.ZeroOrOne)
      .addOutEdge(edge = ast, inNode = ret, cardinalityIn = Cardinality.ZeroOrOne)
      .addOutEdge(edge = ast, inNode = block, cardinalityIn = Cardinality.ZeroOrOne)
      .addOutEdge(edge = ast, inNode = jumpTarget)
      .addOutEdge(edge = ast, inNode = unknown)
      .addOutEdge(edge = ast, inNode = controlStructure)
      .addOutEdge(edge = ast, inNode = methodRef, cardinalityIn = Cardinality.One)
      .addOutEdge(edge = ast, inNode = typeRef)
      .addOutEdge(edge = ast, inNode = jumpLabel)

    unknown
      .addOutEdge(edge = ast, inNode = literal)
      .addOutEdge(edge = ast, inNode = modifier)
      .addOutEdge(edge = ast, inNode = local)
      .addOutEdge(edge = ast, inNode = identifier)
      .addOutEdge(edge = ast, inNode = fieldIdentifier)
      .addOutEdge(edge = ast, inNode = ret)
      .addOutEdge(edge = ast, inNode = block)
      .addOutEdge(edge = ast, inNode = jumpTarget)
      .addOutEdge(edge = ast, inNode = unknown)
      .addOutEdge(edge = ast, inNode = controlStructure)

    unknown.addOutEdge(edge = ast, inNode = member)
    methodParameterIn.addOutEdge(edge = ast, inNode = unknown)

    namespaceBlock
      .addOutEdge(edge = ast, inNode = typeDecl, cardinalityIn = Cardinality.ZeroOrOne, stepNameIn = "namespaceBlock")
      .addOutEdge(edge = ast, inNode = method, cardinalityIn = Cardinality.ZeroOrOne)

    namespace.extendz(astNode)

    controlStructure
      .addOutEdge(edge = condition, inNode = literal)
      .addOutEdge(edge = condition, inNode = identifier)
      .addOutEdge(edge = condition, inNode = ret)
      .addOutEdge(edge = condition, inNode = block)
      .addOutEdge(edge = condition, inNode = methodRef)
      .addOutEdge(edge = condition, inNode = typeRef)
      .addOutEdge(edge = condition, inNode = controlStructure)
      .addOutEdge(edge = condition, inNode = jumpTarget)
      .addOutEdge(edge = condition, inNode = unknown)
      .addOutEdge(edge = condition, inNode = controlStructure)

    typeDecl
      .addOutEdge(edge = ast, inNode = typeDecl, cardinalityIn = Cardinality.ZeroOrOne)
      .addOutEdge(edge = ast, inNode = method, cardinalityIn = Cardinality.ZeroOrOne)

    method
      .addOutEdge(edge = ast, inNode = methodParameterOut, cardinalityIn = Cardinality.One, stepNameIn = "method")
      .addOutEdge(edge = ast, inNode = typeDecl, cardinalityIn = Cardinality.ZeroOrOne)
      .addOutEdge(edge = ast, inNode = method, cardinalityIn = Cardinality.ZeroOrOne)

    val callRepr = builder
      .addNodeBaseType(
        name = "CALL_REPR",
        comment = "This is the base class of `CALL` that language implementers may safely ignore."
      )
      .addProperties(name, signature)

    val callNode: NodeType = builder
      .addNodeType(
        name = "CALL",
        comment = """A (function/method/procedure) call. The `METHOD_FULL_NAME` property is the name of the
                    |invoked method (the callee) while the `TYPE_FULL_NAME` is its return type, and
                    |therefore, the return type of the call when viewing it as an expression. For
                    |languages like Javascript, it is common that we may know the (short-) name
                    |of the invoked method, but we do not know at compile time which method
                    |will actually be invoked, e.g., because it depends on a dynamic import.
                    |In this case, we leave `METHOD_FULL_NAME` blank but at least fill out `NAME`,
                    |which contains the method's (short-) name and `SIGNATURE`, which contains
                    |any information we may have about the types of arguments and return value.
                    |""".stripMargin
      )
      .protoId(ProtoIds.CallNode)
      .extendz(callRepr)
      .addProperties(typeFullName)
      .primaryKey(name)

    val expression = builder
      .addNodeBaseType(
        name = "EXPRESSION",
        comment = """`EXPRESSION` is the base class for all nodes that represent code pieces
                    |that can be evaluated.
                    |
                    | Expression may be arguments in method calls. For method calls that do
                    | not involved named parameters, the `ARGUMENT_INDEX` field indicates at
                    | which position in the argument list the expression occurs, e.g., an
                    | `ARGUMENT_INDEX` of 1 indicates that the expression is the first argument
                    | in a method call. For calls that employ named parameters, `ARGUMENT_INDEX`
                    | is set to -1 and the `ARGUMENT_NAME` fields holds the name of the parameter.
                    |""".stripMargin
      )
      .extendz(astNode)

    fieldIdentifier.extendz(expression)
    identifier.extendz(expression)
    literal.extendz(expression)
    block.extendz(expression)
    controlStructure.extendz(expression)
    methodRef.extendz(expression)
    typeRef.extendz(expression)
    ret.extendz(expression)
    unknown.extendz(expression)

    callNode
      .addOutEdge(edge = ast, inNode = callNode)
      .addOutEdge(edge = ast, inNode = identifier)
      .addOutEdge(edge = ast, inNode = fieldIdentifier, cardinalityIn = Cardinality.One)
      .addOutEdge(edge = ast, inNode = literal)
      .addOutEdge(edge = ast, inNode = methodRef)
      .addOutEdge(edge = ast, inNode = typeRef)
      .addOutEdge(edge = ast, inNode = ret)
      .addOutEdge(edge = ast, inNode = block)
      .addOutEdge(edge = ast, inNode = controlStructure)

    ret.addOutEdge(edge = ast, inNode = callNode)
    controlStructure.addOutEdge(edge = ast, inNode = callNode, cardinalityIn = Cardinality.One)
    unknown.addOutEdge(edge = ast, inNode = callNode)
    controlStructure.addOutEdge(edge = condition, inNode = callNode)

    block.addOutEdge(edge = ast, inNode = callNode)

    // To refactor

    identifier
      .addOutEdge(
        edge = ref,
        inNode = local,
        cardinalityOut = Cardinality.ZeroOrOne,
        stepNameIn = "referencingIdentifiers",
        stepNameInDoc = "Places (identifier) where this local is being referenced"
      )
      .addOutEdge(
        edge = ref,
        inNode = methodParameterIn,
        cardinalityOut = Cardinality.ZeroOrOne,
        stepNameIn = "referencingIdentifiers",
        stepNameInDoc = "Places (identifier) where this parameter is being referenced"
      )

    namespaceBlock.addOutEdge(edge = ref, inNode = namespace)

  }

}
