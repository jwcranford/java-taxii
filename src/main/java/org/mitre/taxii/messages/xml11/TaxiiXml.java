/*
Copyright (c) 2014, The MITRE Corporation
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of The MITRE Corporation nor the 
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. 
 */

package org.mitre.taxii.messages.xml11;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.util.JAXBSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SAXDestination;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XsltExecutable;
import net.sf.saxon.s9api.XsltTransformer;

import org.mitre.taxii.messages.xmldsig.Signature;
import org.mitre.taxii.util.Iterators;
import org.mitre.taxii.util.Validation;
import org.mitre.taxii.util.ValidationErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * JAXB utility class.
 * 
 * <p>
 * Note that {@link #validateFast(MessageType)} and {@link #validateAll(MessageType)}
 * perform validation checks beyond those done by the underlying XML schema; thus,
 * these methods should be preferred over doing schema validation during 
 * JAXB marshalling and unmarshalling.  Specifically, 
 * {@link Marshaller#setSchema(Schema)} and 
 * {@link Unmarshaller#setSchema(Schema)} should NOT be used for validation, as
 * the additional code checks would not be performed;
 * instead, {@link #validateFast(MessageType)} or 
 * {@link #validateAll(MessageType)} should be called before marshalling 
 * and after unmarshalling.
 * </p> 
 * 
 * <p>
 * The following examples show how validation should be done before marshalling
 * or after unmarshalling.  
 * </p>
 * 
 * 
 * <h3>Fast Validation before Marshalling</h3>
 *  
 * <pre>
 * try {
 *   TaxiiXml taxiiXml = new TaxiiXml();
 *   Validation results = taxiiXml.validateFast(msg, true);
 *   if (results.hasWarnings()) {
 *     System.out.print("Validation warnings: ");
 *     System.out.println(results.getAllWarnings());
 *   }
 *   Marshaller m = taxiiXml.createMarshaller(true);
 *   m.marshal(msg, System.out);
 * }
 * catch (SAXParseException e) {
 *   System.err.print("Validation error: ");
 *   System.err.println(TaxiiXml.formatException(e));
 * } 
 * catch (SAXException e) {
 *   System.err.print("Validation error: ");
 *   e.printStackTrace();
 * }
 * </pre>
 * 
 * 
 * <h3>Fast Validation after Unmarshalling</h3>
 * 
 * <pre>
 * try {
 *   TaxiiXml taxiiXml = new TaxiiXml();
 *   Unmarshaller u = taxiiXML.getJaxbContext().createUnmarshaller();
 *   MessageType msg = (MessageType) u.unmarshal(input);
 *   Validation results = taxiiXml.validateFast(msg, true);
 *   if (results.hasWarnings()) {
 *     System.out.print("Validation warnings: ");
 *     System.out.println(results.getAllWarnings());
 *   }
 *   // do something with msg
 * }
 * catch (SAXParseException e) {
 *   System.err.print("Validation error: ");
 *   System.err.println(TaxiiXml.formatException(e));
 * } 
 * catch (SAXException e) {
 *   System.err.print("Validation error: ");
 *   e.printStackTrace();
 * }
 * </pre>
 * 
 *
 * <h3>Comprehensive Validation before Marshalling</h3>
 * 
 * <pre>
 * try {
 *   TaxiiXml taxiiXml = new TaxiiXml();
 *   Validation results = taxiiXml.validateAll(msg, true);
 *   if (results.isSuccess()) {
 *     if (results.hasWarnings()) {
 *     System.out.print("Validation warnings: ");
 *       System.out.println(results.getAllWarnings());
 *     }
 *     Marshaller m = taxiiXml.createMarshaller(true);
 *     m.marshal(msg, System.out);
 *   }
 *   else {  // validation errors and warnings together
 *     System.err.print("Validation results: ");
 *     System.err.println(results.getAllErrorsAndWarnings());
 *   }
 * }
 * catch (SAXParseException e) {
 *   System.err.print("Fatal validation error: ");
 *   System.err.println(TaxiiXml.formatException(e));
 * } 
 * catch (SAXException e) {
 *   System.err.print("Fatal validation error: ");
 *   e.printStackTrace();
 * }
 * </pre>
 * 
 * 
 * <h3>Comprehensive Validation after Unmarshalling</h3>
 * 
 * <pre>
 * try {
 *   TaxiiXml taxiiXml = new TaxiiXml();
 *   Unmarshaller u = taxiiXML.getJaxbContext().createUnmarshaller();
 *   MessageType msg = (MessageType) u.unmarshal(input);
 *   Validation results = taxiiXml.validateAll(msg, true);
 *   if (results.isSuccess()) {
 *     if (results.hasWarnings()) {
 *       System.out.print("Validation warnings: ");
 *       System.out.println(results.getAllWarnings());
 *     }
 *     // do something with msg
 *   }
 *   else {  // validation errors and warnings together
 *     System.err.print("Validation results: ");
 *     System.err.println(results.getAllErrorsAndWarnings());
 *   }
 * }
 * catch (SAXParseException e) {
 *   System.err.print("Fatal validation error: ");
 *   System.err.println(TaxiiXml.formatException(e));
 * } 
 * catch (SAXException e) {
 *   System.err.print("Fatal validation error: ");
 *   e.printStackTrace();
 * }
 * </pre>
 * 
 * 
 * @author Jonathan W. Cranford
 */
// TODO copy the above code to a driver to test it out 
public final class TaxiiXml implements StatusDetails {

    private static final String TAXII_SCHEMA_RESOURCE = "/TAXII_XMLMessageBinding_Schema-1.1-xjc.xsd";
    private static final String TAXII_SCHEMATRON_XSLT_RESOURCE = "/TAXII_XMLMessageBinding_Schema-1.1-compiled.xsl";
    
    private final JAXBContext jaxbContext;
    private final Schema taxiiSchema;
    private final XsltExecutable schematronValidator;
    
    /**
     * Default constructor.
     * 
     * @throws RuntimeException
     *              if a deployment error prevents the underlying JAXBContext
     *              from being created, the Schema from being parsed, or 
     *              the validating stylesheet from being compiled.
     */
    public TaxiiXml() {
        List<String> noAddlEntries = Collections.emptyList();
        jaxbContext = newJaxbContext(noAddlEntries);
        taxiiSchema = newSchema();
        schematronValidator = compileValidator();
    }
    
    
    /**
     * Constructor that takes additional JAXB packages, used in initializing 
     * the JAXB Context.
     * 
     * @throws RuntimeException
     *              if a deployment error prevents the underlying JAXBContext
     *              from being created, the Schema from being parsed, or 
     *              the validating stylesheet from being compiled.
     */
    public TaxiiXml(List<String> additionalJaxbPackages) {
        jaxbContext = newJaxbContext(additionalJaxbPackages);
        taxiiSchema = newSchema();
        schematronValidator = compileValidator();
    }
    

    /**
     * set the JAXBContext for the TAXII XML Message Binding 1.1 classes.
     * 
     * @throws RuntimeException 
     *              if a deployment error prevents 
     *              the JAXBContext from being created
     */
    private JAXBContext newJaxbContext(List<String> addlContextEntries) {
        try {      
            List<String> contextEntries = new ArrayList<>();
            contextEntries.add(TaxiiXml.class.getPackage().getName());
            contextEntries.add(Signature.class.getPackage().getName());  
            contextEntries.addAll(addlContextEntries);
            return JAXBContext.newInstance(Iterators.join(contextEntries.iterator(), ":"));
        } catch (JAXBException e) {
            throw new RuntimeException("Deployment error", e);
        }
    }
    
            
    /**
     * Returns a marshaller for the TAXII XML Message Binding 1.1
     * classes.
     * 
     * @param prettyPrint
     *              Returns a marshaller that indents the output if true.
     * @return 
     * @throws JAXBException 
     *              if an error was encountered while creating the Marshaller
     */
    public Marshaller createMarshaller(
            boolean prettyPrint)
            throws JAXBException {
        final Marshaller m = jaxbContext.createMarshaller();
        m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, prettyPrint);
        return m;
    }
    
    
    /**
     * Returns a JAXP Schema that can be used to validate against the TAXII
     * XML Message Binding 1.1 schema.
     * 
     * @throws RuntimeException 
     *              if a deployment error prevents the TAXII Schema from 
     *              being parsed
     */
    private Schema newSchema() {
        final SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        try {
            final URL resource = getClass().getResource(TAXII_SCHEMA_RESOURCE);
            if (resource == null) {
                throw new RuntimeException("Deployment error: can't find TAXII 1.1 schema (" + TAXII_SCHEMA_RESOURCE + ")");
            }
            return sf.newSchema(resource);
        } catch (SAXException e) {
            throw new RuntimeException("Deployment error: can't parse TAXII schema", e);
        }
    }
    
    
    /**
     * Compiles the (Schematron-derived) XSLT stylesheet that implements additional validation checks.
     */
    private XsltExecutable compileValidator() {
        // This method should only be called once in the constructor, and the
        // stylesheet is only compiled once, so we
        // don't cache any of these objects like we normally would.
        final URL resource = getClass().getResource(TAXII_SCHEMATRON_XSLT_RESOURCE);
        if (resource == null) {
            throw new RuntimeException("Deployment error: can't find additional TAXII validator (" + TAXII_SCHEMATRON_XSLT_RESOURCE + ")");
        }
        try {
            final boolean useLicensedEdition = false;
            return new Processor(useLicensedEdition).newXsltCompiler()
                    .compile(new StreamSource(resource.toString()));
        } 
        catch (SaxonApiException e) {
            throw new RuntimeException("Deployment error: The validator stylesheet contains static errors or it cannot be read. See the standard error output for details.",
                    e);
        }
    }

    
    /**
     * Validates the given message, returning all accumulated errors and warnings.
     * 
     * @param m
     *       The message to validate
     * @param checkSpecConformance      
     *       Check conformance to specification beyond what XML Schema provides.
     *
     * @returns 
     *       The validation results, including all errors and warnings.  
     * @throws JAXBException 
     *      If the message couldn't be validated because of an underlying JAXB error
     * @throws IOException 
     *      If the underlying {@link org.xml.sax.XMLReader} throws an
     *      {@link IOException}.
     * @throws SAXException 
     *      on any fatal validation error 
     */
    public Validation validateAll(MessageType m, boolean checkSpecConformance) 
            throws JAXBException, SAXException, IOException {
        return validate(m, false, checkSpecConformance);
    }
    
    
    /**
     * Validates the given message, throwing a SAXException on the first
     * validation error encountered.
     * 
     * @param m
     *       The message to validate
     * @param checkSpecConformance      
     *       Check conformance to specification beyond what XML Schema provides.
     *
     * @returns 
     *       The validation results, including any warnings.  If this method 
     *       returns successfully, then isSuccess() on the returned object will
     *       always return true.  
     * @throws JAXBException 
     *      If the message couldn't be validated because of an underlying JAXB error
     * @throws IOException 
     *      If the underlying {@link org.xml.sax.XMLReader} throws an
     *      {@link IOException}.
     * @throws SAXException 
     *      On the first validation error 
     */
    public Validation validateFast(MessageType m, boolean checkSpecConformance) 
            throws JAXBException, SAXException, IOException {
        return validate(m, true, checkSpecConformance);
    }
    

    /**
     * Marshals a given TAXII Message to an XML String. 
     * 
     * @throws JAXBException 
     *           if any unexpected problem occurs during marshalling
     */
    public String marshalToString(final Marshaller m, final MessageType msg)
            throws JAXBException {
        final StringWriter sw = new StringWriter();
        m.marshal(msg, sw);
        return sw.toString();
    }
    
    
    public static String formatException(SAXParseException e) {
        return String.format("(%s, line %d, column %d) %s",
                    e.getSystemId(),
                    e.getLineNumber(),
                    e.getColumnNumber(),
                    e.getMessage());
    }


    /**
     * Returns the JAXB Context.
     */
    public JAXBContext getJaxbContext() {
        return jaxbContext;
    }
    
    
   /**
    * Validates the given message.
    * 
    * <p>Technique derived from 
    * <a href="http://blog.bdoughan.com/2010/11/validate-jaxb-object-model-with-xml.html">Validate a JAXB Object Model With an XML Schema</a>
    * blog post.</p>
    *
    * @param m
    *       Message to validate
    * @param failFast
    *       If true, then a SAXException will be thrown on the first error 
    *       encountered; otherwise,
    *       all errors and warnings are returned in the Validation object.
    * @param checkSpecConformance      
    *       Check conformance to specification beyond what XML Schema provides.
    *       
    * @returns 
    *       The validation results.  If failFast is true,
    *       then a successful return indicates success, results.isSuccess() will 
    *       be true, and results will include any 
    *       validation warnings. If failFast is false, then the 
    *       results will include all errors and warnings.  
    * @throws JAXBException 
    *      If the message couldn't be validated because of an underlying JAXB error
    * @throws IOException 
    *      If the underlying {@link org.xml.sax.XMLReader} throws an
    *      {@link IOException}.
    * @throws SAXException 
    *      If failFast is true, then a SAXException will be thrown on the first
    *      validation error encountered. If failFast is false, then a 
    *      SAXException indicates a fatal error. 
    */
    private Validation validate(MessageType m, 
            boolean failFast, 
            boolean checkSpecConformance) 
            throws JAXBException, SAXException, IOException {
        JAXBSource source = new JAXBSource(jaxbContext, m);
        Validator validator = taxiiSchema.newValidator();
        final Validation results = new Validation();
        final ValidationErrorHandler errorHandler = new ValidationErrorHandler(results, failFast);
        validator.setErrorHandler(errorHandler);
        validator.validate(source);
        
        if (checkSpecConformance) {
            checkConformance(m, errorHandler);
            if (results.isFailure() && failFast) {
                throw new SAXException("Conformance failure: " + results.getAllErrors());
            }
        }
        
        return results;
    }

    
    /**
     * Check conformance to TAXII specification beyond what XML Schema provides. 
     */
    private void checkConformance(MessageType m, 
            ValidationErrorHandler errorHandler) {
        final XsltTransformer transformer = schematronValidator.load();
        transformer.setMessageListener(errorHandler);
        try {
            transformer.setSource(new JAXBSource(jaxbContext, m));
            transformer.setDestination(new SAXDestination(new DefaultHandler()));
            transformer.transform();
        } 
        catch (SaxonApiException | JAXBException e) {
            errorHandler.getResults().addError("Conformance error: " + e.getMessage());
        } 
    }
        
}
