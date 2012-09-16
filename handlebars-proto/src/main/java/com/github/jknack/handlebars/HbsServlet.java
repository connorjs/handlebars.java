/**
 * Copyright (c) 2012 Edgar Espina
 *
 * This file is part of Handlebars.java.
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
package com.github.jknack.handlebars;

import static org.apache.commons.io.FilenameUtils.removeExtension;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.URI;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;

import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jknack.handlebars.HbsServer.Option;
import com.github.jknack.handlebars.context.FieldValueResolver;
import com.github.jknack.handlebars.context.JavaBeanValueResolver;
import com.github.jknack.handlebars.context.MapValueResolver;

/**
 * Prepare, compile and merge handlebars templates.
 *
 * @author edgar.espina
 */
public class HbsServlet extends HttpServlet {

  /**
   * The default serial uid.
   */
  private static final long serialVersionUID = 1L;

  /**
   * The handlebars object.
   */
  private final Handlebars handlebars;

  /**
   * The object mapper.
   */
  private final ObjectMapper mapper = new ObjectMapper();

  /**
   * The server options.
   */
  private final Map<String, Option> options;

  /**
   * Creates a new {@link HbsServlet}.
   *
   * @param handlebars The handlebars object.
   * @param options The server options.
   */
  public HbsServlet(final Handlebars handlebars,
      final Map<String, Option> options) {
    this.handlebars = handlebars;
    this.options = options;

    mapper.configure(Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
    mapper.configure(Feature.ALLOW_COMMENTS, true);
  }

  @Override
  protected void doGet(final HttpServletRequest request,
      final HttpServletResponse response)
      throws ServletException, IOException {
    Writer writer = null;

    try {
      Template template =
          handlebars.compile(
              URI.create(removeExtension(requestURI(request))));
      @SuppressWarnings("rawtypes")
      Map data = mapper.readValue(json(request), Map.class);

      writer = response.getWriter();
      String output = template.apply(data);
      writer.write(output);
      response.setContentType(options.get("-content-type").getValue());
    } catch (HandlebarsException ex) {
      handlebarsError(ex, response);
    } catch (FileNotFoundException ex) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND,
          "NOT FOUND: " + ex.getMessage());
    } finally {
      IOUtils.closeQuietly(writer);
    }
  }

  /**
   * Remove context path from the request's URI.
   *
   * @param request The current request.
   * @return Same as {@link HttpServletRequest#getRequestURI()} without context
   *         path.
   */
  private String requestURI(final HttpServletRequest request) {
    String requestURI =
        request.getRequestURI().replace(request.getContextPath(), "");
    return requestURI;
  }

  /**
   * Deal with a {@link HandlebarsException}.
   *
   * @param ex The handlebars exception.
   * @param response The http response.
   * @throws IOException If something goes wrong.
   */
  private void handlebarsError(final HandlebarsException ex,
      final HttpServletResponse response) throws IOException {

    HandlebarsError error = ex.getError();
    Handlebars handlebars = new Handlebars();
    StringHelpers.register(handlebars);

    Template template =
        handlebars.compile(URI.create("/error-pages/error"));

    int firstLine = 1;
    if (error != null) {
      if (ex.getCause() != null) {
        firstLine = error.line;
      } else {
        firstLine = Math.max(1, error.line - 1);
      }
    }
    PrintWriter writer = null;
    writer = response.getWriter();
    template.apply(
        Context
            .newBuilder(ex)
            .resolver(MapValueResolver.INSTANCE, FieldValueResolver.INSTANCE,
                JavaBeanValueResolver.INSTANCE)
            .combine("lang", "Xml")
            .combine("firstLine", firstLine).build()
        , writer);

    IOUtils.closeQuietly(writer);
  }

  /**
   * Try to load a <code>js</code> file that matches the given request.
   *
   * @param request The requested object.
   * @return A json string.
   * @throws IOException If the file isn't found.
   */
  private String json(final HttpServletRequest request) throws IOException {
    try {
      return read(removeExtension(requestURI(request)) + ".js");
    } catch (FileNotFoundException ex) {
      return "{}";
    }
  }

  /**
   * Read a file from the servlet context.
   *
   * @param uri The requested file.
   * @return The string content.
   * @throws IOException If the file is not found.
   */
  private String read(final String uri) throws IOException {
    InputStream input = null;
    try {
      input = getServletContext().getResourceAsStream(uri);
      if (input == null) {
        throw new FileNotFoundException(options.get("-dir").getValue() + uri);
      }
      return IOUtils.toString(input);
    } finally {
      IOUtils.closeQuietly(input);
    }
  }

  @Override
  protected void doPost(final HttpServletRequest req,
      final HttpServletResponse resp)
      throws ServletException, IOException {
    doGet(req, resp);
  }

}
