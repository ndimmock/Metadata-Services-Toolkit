/**
  * Copyright (c) 2009 eXtensible Catalog Organization
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the MIT/X11 license. The text of the
  * license can be found at http://www.opensource.org/licenses/mit-license.php and copy of the license can be found on the project
  * website http://www.extensiblecatalog.org/.
  *
  */

package xc.mst.services.transformation.bo;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;

import org.apache.log4j.Logger;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.output.XMLOutputter;
import org.jdom.transform.JDOMSource;

import sun.reflect.ReflectionFactory.GetReflectionFactoryAction;

import xc.mst.bo.provider.Format;
import xc.mst.bo.record.Record;
import xc.mst.constants.Constants;
import xc.mst.dao.DatabaseConfigException;
import xc.mst.manager.repository.FormatService;
import xc.mst.services.impl.GenericMetadataService;
import xc.mst.services.transformation.TransformationServiceConstants.FrbrLevel;
import xc.mst.utils.MSTConfiguration;



/**
 * This class contains methods to add, update, and get the values of various XC record fields.
 *
 * @author Eric Osisek
 */
public class AggregateXCRecord
{
	/**
	 * The logger object
	 */
	protected static Logger log = Logger.getLogger(Constants.LOGGER_PROCESSING);

	/**
	 * The namespace for XML Schema Instance
	 */
	public static final Namespace XSI_NAMESPACE = Namespace.getNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance");

	/**
	 * The namespace for the XC Schema
	 */
	public static final Namespace XC_NAMESPACE = Namespace.getNamespace("xc", "http://www.extensiblecatalog.info/Elements");

	/**
	 * The namespace for the RD Vocabulary
	 */
	public static final Namespace RDVOCAB_NAMESPACE = Namespace.getNamespace("rdvocab", "http://rdvocab.info/Elements");

	/**
	 * The namespace for DC Terms
	 */
	public static final Namespace DCTERMS_NAMESPACE = Namespace.getNamespace("dcterms", "http://purl.org/dc/terms/");

	/**
	 * The namespace for RDA Roles
	 */
	public static final Namespace RDAROLE_NAMESPACE = Namespace.getNamespace("rdarole", "http://rdvocab.info/roles");

	// BDA 2010-09-10- I'm making some of these instance variables public because I moved a bunch of logic
	// to XCRecordService and it's either make these public or create a bunch of getters and setters.  Perhaps
	// I'll add the getters/setters later, but for now... public
	/**
	 * The element for the work FRBR level
	 */
	public Element xcWorkElement = (new Element("entity", XC_NAMESPACE)).setAttribute("type", "work");

	/**
	 * The element for the expression FRBR level
	 */
	public Element xcExpressionElement = (new Element("entity", XC_NAMESPACE)).setAttribute("type", "expression");

	/**
	 * The element for the manifestation FRBR level
	 */
	public Element xcManifestationElement = (new Element("entity", XC_NAMESPACE)).setAttribute("type", "manifestation");

	/**
	 * The element for the item FRBR level
	 */
	public Element xcItemElement = (new Element("entity", XC_NAMESPACE)).setAttribute("type", "item");

	/**
	 * An XC record can contain extra work elements describing works within the manifestation (such as the tracks on a CD.)
	 * Data on which information belongs in which of these work elements is maintained in the MARCXML linking fields.
	 * This HashMap maps a linking field value to the specific work element build for that linking field
	 */
	public HashMap<String, Element> linkingFieldToWorkElement = new HashMap<String, Element>();

	/**
	 * A list of holdings elements for this XC record
	 */
	public HashSet<Element> holdingsElements = new HashSet<Element>();

	/**
	 * A list of expression elements for this XC record
	 */
	public ArrayList<Hashtable<String,Element>> subElementsOfExpressionElements = new ArrayList<Hashtable<String,Element>>();

	/**
	 * A list of work elements for this XC record
	 */
	public ArrayList<Hashtable<String,Element>> subElementsOfWorkElements = new ArrayList<Hashtable<String,Element>>();

	/**
	 * Used to ensure that duplicates are not added to the XC record
	 */
	public HashSet<String> addedElements = new HashSet<String>();

	/**
	 * The root element for the XC Record
	 */
	public Element xcRootElement = null;

	/**
	 * The MARC XML Document we're managing
	 */
	public Document xcXml = null;

	/**
	 * True iff the record contains non-holdings components
	 */
	public boolean hasBibInfo = false;

	/**
	 * The xc:recordID's type followed by its value
	 */
	public String xcRecordId = null;
	
	public Format xcFormat = null;
	
	/*
	 * BDA - These contain the ids of previously output records.
	 */
	protected List<Long> previousWorkIds = null;
	protected List<Long> previousExpressionIds = null;
	protected Long previousManifestationId = null;
	protected List<Long> previousHoldingIds = null;

	/**
	 * Gets the xc:recordID's type followed by its value
	 *
	 * @return The xc:recordID's type followed by its value
	 */
	public String getXcRecordId()
	{
		if(xcRecordId == null)
		{

		}

		return xcRecordId;
	}

	/**
	 * Return true iff the record contains non-holdings components
	 *
	 * @return True iff the record contains non-holdings components
	 */
	public boolean getHasBibInfo()
	{
		return hasBibInfo;
	}

	/**
	 * Constructs an empty XCRecord.
	 */
	public AggregateXCRecord()
	{
		try {
			xcFormat = ((FormatService)MSTConfiguration.getInstance().getBean("FormatService")).getFormatByName("xc");
		} catch(DatabaseConfigException dce) {
			log.error("Unable to connect to database using the parameters in configuration file.");
		}
	} // end constructor

	/**
	 * Constructs a XCRecord based on a the passed XML file which follows the XC schema.
	 *
	 * @param xcXml The XC record we're managing
	 */
	@SuppressWarnings("unchecked")
	public AggregateXCRecord(Document xcXml)
	{
		this();
		// True if we've set the main work element, false otherwise
		boolean workSet = false;

		// An artifical linking field for adding extra work elements
		int artLinkingField = 1;

		// Get the content of the xc record
		List<Element> elements = xcXml.getRootElement().getChildren();

		for(Element element : elements)
		{
			String frbrLevel = element.getAttributeValue("type");

			if(frbrLevel.equals("work"))
			{
				hasBibInfo = true;

				if(!workSet)
				{
					xcWorkElement = element;
					workSet = true;
				}
				else
					linkingFieldToWorkElement.put("" + artLinkingField++, element);
			}
			else if(frbrLevel.equals("expression"))
			{
				xcExpressionElement = element;
				hasBibInfo = true;
			}
			else if(frbrLevel.equals("manifestation"))
			{
				xcManifestationElement = element;
				hasBibInfo = true;
			}
			else if(frbrLevel.equals("item"))
				xcItemElement = element;
			else if(frbrLevel.equals("holdings"))
				holdingsElements.add(element);
		}
	} // end constructor
}
