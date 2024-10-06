package com.github.kettoleon.llm.sandbox.common.util;

import lombok.extern.slf4j.Slf4j;
import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.html.HtmlWriter;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Slf4j
public class MarkdownUtils {

    public static final List<Extension> EXTENSIONS = List.of(TablesExtension.create(), AutolinkExtension.create(), StrikethroughExtension.create());

    public static final HtmlRenderer RENDERER = HtmlRenderer.builder()
            .extensions(EXTENSIONS)
            .escapeHtml(true)
            .nodeRendererFactory(IndentedCodeBlockNodeRenderer::new)
            .nodeRendererFactory(LineBreakPreservingNodeRenderer::new)
            .build();

    public static final Parser PARSER = Parser.builder()
            .extensions(EXTENSIONS)
            .build();

    public static String markdownToHtml(String markdownResult) {
        try {
            return RENDERER.render(PARSER.parse(markdownResult));
        } catch (Exception e) {
            log.warn("Error rendering markdown: {}", markdownResult, e);
            return markdownResult;
        }
    }

    static class IndentedCodeBlockNodeRenderer implements NodeRenderer {

        private final HtmlWriter html;

        IndentedCodeBlockNodeRenderer(HtmlNodeRendererContext context) {
            this.html = context.getWriter();
        }

        @Override
        public Set<Class<? extends Node>> getNodeTypes() {
            // Return the node types we want to use this renderer for.
            return Collections.<Class<? extends Node>>singleton(IndentedCodeBlock.class);
        }

        @Override
        public void render(Node node) {
            // We only handle one type as per getNodeTypes, so we can just cast it here.
            IndentedCodeBlock codeBlock = (IndentedCodeBlock) node;
            html.line();
            html.tag("pre");
            html.text(codeBlock.getLiteral());
            html.tag("/pre");
            html.line();
        }
    }

    static class LineBreakPreservingNodeRenderer implements NodeRenderer {

        private final HtmlWriter html;

        LineBreakPreservingNodeRenderer(HtmlNodeRendererContext context) {
            this.html = context.getWriter();
        }

        @Override
        public Set<Class<? extends Node>> getNodeTypes() {
            // Return the node types we want to use this renderer for.
            return Collections.singleton(SoftLineBreak.class);
        }

        @Override
        public void render(Node node) {
            html.tag("/br");
            html.line();
        }
    }
}
