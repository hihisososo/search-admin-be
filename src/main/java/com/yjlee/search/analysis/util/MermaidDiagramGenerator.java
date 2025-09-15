package com.yjlee.search.analysis.util;

import com.yjlee.search.analysis.domain.TokenInfo;

import java.util.*;
import java.util.stream.Collectors;

public final class MermaidDiagramGenerator {

  private MermaidDiagramGenerator() {
  }
  
  private static final String START_NODE = "START";
  private static final String END_NODE = "END";
  private static final String AND_NODE = "AND";
  private static final String GRAPH_HEADER = "graph LR\n";
  private static final String ARROW = "-->";
  private static final String NODE_CLASS_START_END = "startEnd";
  private static final String NODE_CLASS_AND = "andNode";
  private static final String STYLE_START_END = "    classDef startEnd fill:#e1f5fe,stroke:#01579b,stroke-width:3px\n";
  private static final String STYLE_AND_NODE = "    classDef andNode fill:#fff3e0,stroke:#e65100,stroke-width:2px\n";
  private static final String STYLE_ORIGINAL = "    classDef original stroke:#2e7d32,stroke-width:3px,color:#1b5e20\n";
  private static final String STYLE_SYNONYM = "    classDef synonym stroke:#7b1fa2,stroke-width:2px,stroke-dasharray: 5 5,color:#4a148c\n";

  public static String generate(List<TokenInfo> tokens) {
    if (tokens.isEmpty()) {
      return generateEmptyDiagram();
    }

    StringBuilder mermaid = new StringBuilder();
    appendHeader(mermaid);
    appendStyles(mermaid);

    Map<Integer, Integer> positionMapping = createPositionMapping(tokens);
    appendNodes(mermaid, positionMapping);
    appendTokens(mermaid, tokens, positionMapping);

    return mermaid.toString();
  }

  private static String generateEmptyDiagram() {
    return GRAPH_HEADER + String.format("    %s((\"%s\")) %s %s((\"%s\"))\n",
        START_NODE, START_NODE, ARROW, END_NODE, END_NODE);
  }

  private static void appendHeader(StringBuilder mermaid) {
    mermaid.append(GRAPH_HEADER);
  }

  private static void appendStyles(StringBuilder mermaid) {
    mermaid.append(STYLE_START_END);
    mermaid.append(STYLE_AND_NODE);
    mermaid.append(STYLE_ORIGINAL);
    mermaid.append(STYLE_SYNONYM);
    mermaid.append("\n");
  }

  private static Map<Integer, Integer> createPositionMapping(List<TokenInfo> tokens) {
    Set<Integer> hasOutgoingToken =
        tokens.stream().map(TokenInfo::getPosition).collect(Collectors.toSet());

    Set<Integer> allPositions = new HashSet<>();
    for (TokenInfo token : tokens) {
      allPositions.add(token.getPosition());
      allPositions.add(token.getEndPosition());
    }

    Map<Integer, Integer> positionMapping = new HashMap<>();
    TreeSet<Integer> sortedPositions = new TreeSet<>(allPositions);
    int mappedPos = 0;

    for (Integer pos : sortedPositions) {
      positionMapping.put(pos, mappedPos);
      if (hasOutgoingToken.contains(pos)) {
        mappedPos++;
      }
    }

    return positionMapping;
  }

  private static void appendNodes(StringBuilder mermaid, Map<Integer, Integer> positionMapping) {
    Integer maxMappedPosition =
        positionMapping.values().stream().max(Integer::compareTo).orElse(0);

    for (Map.Entry<Integer, Integer> entry : positionMapping.entrySet()) {
      int remappedPos = entry.getValue();
      String nodeName = getNodeName(remappedPos, maxMappedPosition);
      String nodeClass = getNodeClass(remappedPos, maxMappedPosition);

      mermaid.append(String.format("    %d((\"%s\"))\n", remappedPos, nodeName));
      mermaid.append(String.format("    class %d %s\n", remappedPos, nodeClass));
    }
    mermaid.append("\n");
  }

  private static String getNodeName(int position, int maxPosition) {
    if (position == 0) return START_NODE;
    if (position == maxPosition) return END_NODE;
    return AND_NODE;
  }

  private static String getNodeClass(int position, int maxPosition) {
    if (position == 0 || position == maxPosition) return NODE_CLASS_START_END;
    return NODE_CLASS_AND;
  }

  private static void appendTokens(
      StringBuilder mermaid, List<TokenInfo> tokens, Map<Integer, Integer> positionMapping) {

    List<TokenInfo> originalTokens = filterAndSortTokens(tokens, false);
    List<TokenInfo> synonymTokens = filterAndSortTokens(tokens, true);

    if (!originalTokens.isEmpty()) {
      appendTokenList(mermaid, originalTokens, positionMapping);
    }

    if (!synonymTokens.isEmpty()) {
      mermaid.append("\n");
      appendTokenList(mermaid, synonymTokens, positionMapping);
    }
  }

  private static List<TokenInfo> filterAndSortTokens(List<TokenInfo> tokens, boolean synonyms) {
    return tokens.stream()
        .filter(t -> synonyms == t.isSynonym())
        .sorted(
            (a, b) -> {
              int posCompare = Integer.compare(a.getPosition(), b.getPosition());
              return posCompare != 0 ? posCompare : a.getToken().compareTo(b.getToken());
            })
        .collect(Collectors.toList());
  }

  private static void appendTokenList(
      StringBuilder mermaid,
      List<TokenInfo> tokens,
      Map<Integer, Integer> positionMapping) {
    for (TokenInfo token : tokens) {
      String label = token.getToken();
      int fromPos = positionMapping.get(token.getPosition());
      int toPos = positionMapping.get(token.getEndPosition());

      mermaid.append(String.format("    %d %s|%s| %d\n", fromPos, ARROW, label, toPos));
    }
  }

}