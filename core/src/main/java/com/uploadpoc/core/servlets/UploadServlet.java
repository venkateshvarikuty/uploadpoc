package com.uploadpoc.core.servlets;

import com.day.cq.dam.api.AssetManager;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.osgi.service.component.annotations.Component;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;

@Component(service = Servlet.class, property = {
                "sling.servlet.paths=/bin/upload",
                "sling.servlet.methods=POST"
})
@MultipartConfig
public class UploadServlet extends SlingAllMethodsServlet {

        @Override
        protected void doPost(SlingHttpServletRequest request,
                        SlingHttpServletResponse response)
                        throws ServletException, IOException {

                String client = request.getParameter("client");
                String bookingId = request.getParameter("bookingId");
                String caseId = request.getParameter("caseId");

                Part filePart = request.getPart("file");

                if (filePart == null) {
                        response.getWriter().write("No file uploaded");
                        return;
                }

                String fileName = filePart.getSubmittedFileName();

                InputStream inputStream = filePart.getInputStream();

                String folderPath = "/content/dam/uploads/"
                                + client
                                + "/bookings/"
                                + bookingId
                                + "/cases/"
                                + caseId;

                String assetPath = folderPath + "/" + fileName;

                ResourceResolver resolver = request.getResourceResolver();

                AssetManager assetManager = resolver.adaptTo(AssetManager.class);

                assetManager.createAsset(
                                assetPath,
                                inputStream,
                                filePart.getContentType(),
                                true);

                response.setContentType("text/plain");

                response.getWriter().write(
                                "Uploaded Successfully: " + assetPath);
        }
}