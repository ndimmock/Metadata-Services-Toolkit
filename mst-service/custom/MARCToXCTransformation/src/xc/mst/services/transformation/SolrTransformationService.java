/**
  * Copyright (c) 2009 eXtensible Catalog Organization
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the MIT/X11 license. The text of the
  * license can be found at http://www.opensource.org/licenses/mit-license.php and copy of the license can be found on the project
  * website http://www.extensiblecatalog.org/.
  *
  */
 
package xc.mst.services.transformation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;

import org.apache.log4j.Logger;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jdom.Namespace;

import xc.mst.constants.Constants;
import xc.mst.services.GenericMetadataService;
import xc.mst.services.ServiceValidationException;
import xc.mst.services.transformation.TransformationServiceConstants.FrbrLevel;
import xc.mst.services.transformation.bo.AggregateXCRecord;
import xc.mst.services.transformation.bo.MarcXmlRecord;
import xc.mst.services.transformation.dao.TransformationDAO;
import xc.mst.services.transformation.service.XCRecordService;
import xc.mst.utils.XmlHelper;

/**
 * A Metadata Service which for each unprocessed marcxml record creates an XC schema
 * record from the data in the unprocessed record.
 *
 * @author Eric Osisek
 */
public abstract class SolrTransformationService extends GenericMetadataService {

	protected final static Logger LOG = Logger.getLogger(SolrTransformationService.class);

	protected Namespace marcNamespace = Namespace.getNamespace("marc", "http://www.loc.gov/MARC21/slim");
	
	protected XCRecordService XCRecordService = null;
	
	public XCRecordService getXCRecordService() {
		return XCRecordService;
	}

	public void setXCRecordService(XCRecordService xCRecordService) {
		XCRecordService = xCRecordService;
	}
	
	public TransformationDAO getTransformationDAO() {
		return getXCRecordService().getTransformationDAO();
	}

	protected XmlHelper xmlHelper = new XmlHelper();
	
	/**
	 * A map containing values for $4 subfields which we should treat as
	 * roles and the rdarole they represent.
	 */
	protected HashMap<String, String> roles = new HashMap<String, String>();
	
	/**
	 * This is used to ensure that subfields from the same source get mapped to the same FRBR Work element
	 */
	protected int artificialLinkingId = 0;

	// The following HashSets are used to prevent duplicate values from being added to the XC record

	protected HashMap<String, Element> linkedCreatorFields = new HashMap<String, Element>();
	
	protected boolean processingHeldRecords = false;
	
	/**
	 * Org code used 
	 */
	protected String orgCode = "";
	
	/**
	 * Construct a TransformationService Object
	 */
	public void init()
	{
		// Initialize the list of roles
		roles.put("aut", "author");
		roles.put("lbt", "author");
		roles.put("lyr", "author");
		roles.put("cmp", "composer");
		roles.put("com", "compiler");
		roles.put("art", "artist");
		roles.put("ths", "thesisAdvisor");
		roles.put("drt", "director");
		roles.put("edt", "editor");
		roles.put("ill", "illustrator");
		roles.put("prf", "performer");
		roles.put("act", "performer");
		roles.put("dnc", "performer");
		roles.put("nrt", "performer");
		roles.put("voc", "performer");
		roles.put("itr", "performer");
		roles.put("cnd", "performer");
		roles.put("mod", "performer");
		roles.put("pro", "producer");
		roles.put("trl", "translator");
	}
	
	protected void holdingsProcess004(MarcXmlRecord transformMe, AggregateXCRecord transformInto) {
		List<String> the004s = transformMe.get004s();
		if (the004s != null && the004s.size() > 0) {
			transformInto.getReferencedBibs().addAll(the004s);
		}
	}

	protected void holdingsProcess014(MarcXmlRecord transformMe, AggregateXCRecord transformInto) {
		List<String> the014s = transformMe.get014s("1", "a");
		if (the014s != null && the014s.size() > 0) {
			transformInto.getReferencedBibs().addAll(the014s);
		}
	}

	/**
	 * Processes the 010 field from the MarcXmlRecord we're transforming.
	 * This becomes the xc:recordID field with a type of LCCN at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process010(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an xc:recordID with a type of LCCN based on the 010 $a values
		return processFieldBasic(transformMe, transformInto, "010", 'a', "recordID", AggregateXCRecord.XC_NAMESPACE, new Attribute("type", "LCCN"), FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 015 field from the MarcXmlRecord we're transforming.
	 * This becomes the xc:identifier field with a type equal to the 015's $2 value at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process015(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an xc:identifier based on the 015 $a values with a type based on the corrosponding 015 $2 value
		return processFieldAttributeFromSubfield(transformMe, transformInto, "015", 'a', "identifier", AggregateXCRecord.XC_NAMESPACE, new Attribute("type", "null"), '2', null, FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 016 field from the MarcXmlRecord we're transforming.
	 * This becomes the xc:identifier field with a type equal to the 016's $2 value at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process016(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an xc:identifier based on the 015 $a values with a type based on the corrosponding 015 $2 value
		return processFieldAttributeFromSubfield(transformMe, transformInto, "016", 'a', "identifier", AggregateXCRecord.XC_NAMESPACE, new Attribute("type", "null"), '2', "LAC", FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 022 field from the MarcXmlRecord we're transforming.
	 * This becomes the xc:identifier field with a type of LCCN at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process022(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an identifier with a type of ISSN based on the 022 $a values
		transformInto = processFieldBasic(transformMe, transformInto, "022", 'a', "identifier", AggregateXCRecord.XC_NAMESPACE, new Attribute("type", "ISSN"), FrbrLevel.MANIFESTATION);

		// Create an identifier with a type of ISSN-L based on the 022 $l values
		return processFieldBasic(transformMe, transformInto, "022", 'l', "identifier", AggregateXCRecord.XC_NAMESPACE, new Attribute("type", "ISSN-L"), FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 024 field from the MarcXmlRecord we're transforming.
	 * This becomes the xc:identifier field with a type based on the 1st indicator at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process024(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Setup the map from the 1st indicator to the type of the XC:identifier
		HashMap<String, Attribute> indicatorToType = new HashMap<String, Attribute>();
		indicatorToType.put("0", new Attribute("type", "ISRC"));
		indicatorToType.put("1", new Attribute("type", "UPC"));
		indicatorToType.put("2", new Attribute("type", "ISMN"));
		indicatorToType.put("3", new Attribute("type", "IAN"));
		indicatorToType.put("4", new Attribute("type", "SICI"));
		indicatorToType.put("7", new Attribute("type", "$2"));

		// Create an xc:identifier with a type based on the 1st indicator and a value based on the 024 $a values
		return processFieldAttributeFromIndicator(transformMe, transformInto, "024", 'a', "identifier", AggregateXCRecord.XC_NAMESPACE, "1", indicatorToType, true, FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 028 field from the MarcXmlRecord we're transforming.  This becomes
	 * a field with a name and type attribute based on the 1st indicator at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process028(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Get the 028 datafields (Publisher Numbers) in the MARC XML record
		List<Element> pns = transformMe.getDataFields("028");

		// If there was no 028 datafields, return the unmodified XC record
		if(pns.size() == 0)
			return transformInto;

		// Add elements for each 028 field
		for(Element pn : pns)
		{
			// Get the $a and 1st indicator from the datafield
			List<String> subfieldAs = MarcXmlRecord.getSubfieldOfField(pn, 'a');
			String ind1 = MarcXmlRecord.getIndicatorOfField(pn, "1");

			// If there was no $a subfield or 1st indicator, continue to the next 028 element
			if(subfieldAs.size() <= 0 || ind1 == null || ind1.length() <= 0)
				continue;

			// Get the $a subfield.  This is a non-repeatable subfield, so there should only be one
			String subfieldA = subfieldAs.get(0);

			// Get the name and type of the field we're adding based on the 028's 1st indicator
			String name = null;
			String type = null;
			Namespace namespace = null;
			if(ind1.equals("0"))
			{
				name= "identifier";
				type = "SoundNr";
				namespace = AggregateXCRecord.XC_NAMESPACE;
			}
			else if(ind1.equals("2"))
			{
				name= "plateNumber";
				type = null;
				namespace = AggregateXCRecord.RDVOCAB_NAMESPACE;
			}
			else if(ind1.equals("3"))
			{
				name= "publisherNumber";
				type = null;
				namespace = AggregateXCRecord.RDVOCAB_NAMESPACE;
			}
			else if(ind1.equals("4"))
			{
				name= "identifier";
				type = "VideoNr";
				namespace = AggregateXCRecord.XC_NAMESPACE;
			}
			else
			{
				if(LOG.isDebugEnabled())
					LOG.debug("Unrecognized ind1 " + ind1 + " for an 028 field, skipping the field.");

				// Continue to the next 028.
				continue;
			}

			if(LOG.isDebugEnabled())
				LOG.debug("Adding a manifestation level " + name + " with a type of \"" + type + "\" based on the 028 $a value, which is " + subfieldA);

			// Setup the attribute list for the new field
			ArrayList<Attribute> attributes = new ArrayList<Attribute>();
			if(type != null)
				attributes.add(new Attribute("type", type));

			// Add the identifier to the XC record
			getXCRecordService().addElement(transformInto, name, subfieldA.trim(), namespace, attributes, FrbrLevel.MANIFESTATION);
		}

		// Return the result of this transformation step
		return transformInto;
	}

	/**
	 * Processes the 030 fields from the MarcXmlRecord we're transforming.
	 * These become the xc:identifier fields with a type of CODEN at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process030(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an identifier with a type of CODEN based on the 030 $a values
		return processFieldBasic(transformMe, transformInto, "030", 'a', "identifier", AggregateXCRecord.XC_NAMESPACE, new Attribute("type", "CODEN"), FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 035 fields from the MarcXmlRecord we're transforming.
	 * These become the recordID fields with a type equal to the organization code for the 035 field
	 * at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process035(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Get the control numbers from the 035 $a in the MARC XML record
		List<String> controlNumbers = transformMe.getSubfield("035", 'a');

		// If there was no 035 $a, return the unmodified XC record
		if(controlNumbers == null || controlNumbers.size() == 0)
			return transformInto;

		// Add each control number to the XC record
		for(String controlNumber : controlNumbers)
		{
			// Check that the control number is in the format we're expecting: "(<organization_code>)<control_number>"
			// If it isn't, continue to the next control number
			if(!controlNumber.contains("(") || !controlNumber.contains(")"))
			{
				//log.warn("Encountered an 035 field which we can't process because it did not contain a prefix.  Skipping the malformed 035, which had a value of " + controlNumber);

				continue;
			}

			// Get the value and type of the control number
			String value = controlNumber.substring(controlNumber.indexOf(')') + 1);
			String type = controlNumber.substring(controlNumber.indexOf('(')+1, controlNumber.indexOf(')'));

			if(LOG.isDebugEnabled())
				LOG.debug("Adding a " + FrbrLevel.MANIFESTATION + " level xc:recordID of type " + type + " based on the 035 $a value, which is " + value);

			// Setup the attribute list for the xc:recordID
			ArrayList<Attribute> attributes = new ArrayList<Attribute>();
			attributes.add(new Attribute("type", type));

			// Set the control number on the XC record
			getXCRecordService().addElement(transformInto, "recordID", value.trim(), AggregateXCRecord.XC_NAMESPACE, attributes, FrbrLevel.MANIFESTATION);
		}

		// Return the result of this transformation step
		return transformInto;
	}

	/**
	 * Processes the 037 field from the MarcXmlRecord we're transforming.
	 * This becomes the xc:identifier field with a type equal to the 037's $b value at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process037(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Get the elements with the requested tags in the MARC XML record
		List<Element> elements = transformMe.getDataFields("037");

		// If there were matching elements, return the unmodified XC record
		if(elements.size() == 0)
			return transformInto;

		// Add each subfield to the specified level with the specified tag and attribute
		for(Element element : elements)
		{
			// Get the needed subfields of the element
			List<String> requestedSubfields = MarcXmlRecord.getSubfieldOfField(element, 'a');
			List<String> requestedAttributeSubfields = MarcXmlRecord.getSubfieldOfField(element, 'b');
			List<String> field040SubfieldAs = transformMe.getSubfield("040", 'a');

			if(field040SubfieldAs == null)
				field040SubfieldAs = new ArrayList<String>();

			// If there were were no matches for the requested subfield, we have no data to transform.  In this
			// case continue to the next element
			if(!requestedAttributeSubfields.contains("GPO") && !field040SubfieldAs.contains("GPO"))
				continue;

			// Loop over the values of the requested subfields and add the specified element for each
			for(String value : requestedSubfields)
			{
				// Setup the attribute list for the XC record field
				ArrayList<Attribute> attributes = new ArrayList<Attribute>();
				Attribute elementAttribute = new Attribute("type", "GPO");
				attributes.add(elementAttribute);

				if(LOG.isDebugEnabled())
					LOG.debug("Adding a " + FrbrLevel.MANIFESTATION + " level " + "identifier with a type of \"GPO\" based on the 037 $a value, which is " + value);

				// Add the element to the XC record
				getXCRecordService().addElement(transformInto, "identifier", value.trim(), AggregateXCRecord.XC_NAMESPACE, attributes, FrbrLevel.MANIFESTATION);
			}
		}

		// Return the result
		return transformInto;
	}

	/**
	 * Processes the 050 fields from the MarcXmlRecord we're transforming.
	 * These become the xc:subject fields with a type of dcterms:LCC at the work FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process050(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{

		// Create a xc:subject with a type of dcterms:LCC based on the 050 $a values
		//return processFieldBasic(transformMe, transformInto, "050", 'a', "subject", XCRecord.DCTERMS_NAMESPACE, new Attribute("type", "dcterms:LCC", XCRecord.XSI_NAMESPACE), FrbrLevel.WORK);

		// Get the target subfields MARC XML record
		List<String> subfields = transformMe.getSubfield("050", 'a');

		// If there were no matching subfields return the unmodified XC record
		if(subfields == null || subfields.size() == 0)
			return transformInto;

		// Add each subfield to the specified level with the specified tag and attribute
		for(String value : subfields)
		{
			// Don't process the field if it's third character is not a digit
			if(!value.matches("^..\\d.*$"))
				continue;

			if(LOG.isDebugEnabled())
				LOG.debug("Adding a " + FrbrLevel.WORK + " level " + "subject with a type of \"dcterms:LCC\" based on the 050 $a value, which is " + value);

			// Setup the attribute list
			ArrayList<Attribute> attributes = new ArrayList<Attribute>();
			attributes.add(new Attribute("type", "dcterms:LCC", AggregateXCRecord.XSI_NAMESPACE));

			// Add the element to the XC record
			getXCRecordService().addElement(transformInto, "subject", value.trim(), AggregateXCRecord.DCTERMS_NAMESPACE, attributes, FrbrLevel.WORK);
		}

		// Return the result
		return transformInto;
	}

	/**
	 * Processes the 055 field from the MarcXmlRecord we're transforming.  Each one with a 1st indicator
	 * between 0 and 5 becomes a xc:subject field with a type of dcterms:LCC at the work FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process055(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		
		// Setup the map from the 1st indicator to the type of the XC:identifier
		HashMap<String, Attribute> indicatorToType = new HashMap<String, Attribute>();
		indicatorToType.put("0", new Attribute("type", "dcterms:LCC", AggregateXCRecord.XSI_NAMESPACE));
		indicatorToType.put("1", new Attribute("type", "dcterms:LCC", AggregateXCRecord.XSI_NAMESPACE));
		indicatorToType.put("2", new Attribute("type", "dcterms:LCC", AggregateXCRecord.XSI_NAMESPACE));
		indicatorToType.put("3", new Attribute("type", "dcterms:LCC", AggregateXCRecord.XSI_NAMESPACE));
		indicatorToType.put("4", new Attribute("type", "dcterms:LCC", AggregateXCRecord.XSI_NAMESPACE));
		indicatorToType.put("5", new Attribute("type", "dcterms:LCC", AggregateXCRecord.XSI_NAMESPACE));

		// Create an xc:identifier with a type based on the 1st indicator and a value based on the 024 $a values
		return processFieldAttributeFromIndicator(transformMe, transformInto, "055", 'a', "subject", AggregateXCRecord.DCTERMS_NAMESPACE, "2", indicatorToType, false, FrbrLevel.WORK);
	}

	/**
	 * Processes the 060 field from the MarcXmlRecord we're transforming.
	 * This becomes the dcterms:subject field with a type of dcterms:NLM at the work FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process060(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dcterms:subject with a type of LCCN based on the 060 $a values
		return processFieldBasic(transformMe, transformInto, "060", 'a', "subject", AggregateXCRecord.DCTERMS_NAMESPACE, new Attribute("type", "dcterms:NLM",AggregateXCRecord.XSI_NAMESPACE), FrbrLevel.WORK);
	}

	/**
	 * Processes the 074 field from the MarcXmlRecord we're transforming.
	 * This becomes the xc:identifier field with a type of GPOItem at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process074(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an xc:identifier with a type of GPOItem based on the 074 $a values
		return processFieldBasic(transformMe, transformInto, "074", 'a', "identifier", AggregateXCRecord.XC_NAMESPACE, new Attribute("type", "GPOItem"), FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 082 field from the MarcXmlRecord we're transforming.
	 * This becomes the dcterms:subject field with an xsi:type of dcterms:DDC at the work FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process082(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dcterms:subject with a xsi:type of dcterms:DDC based on the 082 $a values
		return processFieldBasic(transformMe, transformInto, "082", 'a', "subject", AggregateXCRecord.DCTERMS_NAMESPACE, new Attribute("type", "dcterms:DDC", AggregateXCRecord.XSI_NAMESPACE), FrbrLevel.WORK);
	}
	
	/**
	 * Processes the 084 field from the MarcXmlRecord we're transforming.
	 * This becomes the xc:subject field with an type of NDC8 at the work FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process084(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		
		// Get the target subfields MARC XML record
		List<String> subfieldValues = transformMe.getSubfield("084", '2');
		
		if (subfieldValues != null) {
			for (String subfieldValue : subfieldValues) {
				if (subfieldValue.equalsIgnoreCase("NDC8")) {
					// Create an dcterms:subject with a xsi:type of dcterms:DDC based on the 082 $a values
					return processFieldBasic(transformMe, transformInto, "084", 'a', "subject", AggregateXCRecord.XC_NAMESPACE, new Attribute("type", "NDC8"), FrbrLevel.WORK);
				}
			}
		}
		
		return transformInto;
	}

	/**
	 * Processes the 086 field from the MarcXmlRecord we're transforming.
	 * This becomes the xc:identifier field with an type of SuDoc at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process086(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an xc:identifier with a type of SuDoc based on the 086 $a values
		return processFieldBasic(transformMe, transformInto, "086", 'a', "identifier", AggregateXCRecord.XC_NAMESPACE, new Attribute("type", "SuDoc"), FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 090 fields from the MarcXmlRecord we're transforming.
	 * These becomes the xc:subject fields with a type of dcterms:LCC at the work FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process090(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create a xc:subject with a type of dcterms:LCC based on the 090 $a values
		return processFieldBasic(transformMe, transformInto, "090", 'a', "subject", AggregateXCRecord.DCTERMS_NAMESPACE, new Attribute("type", "dcterms:LCC", AggregateXCRecord.XSI_NAMESPACE), FrbrLevel.WORK);
	}

	/**
	 * Processes the 092 field from the MarcXmlRecord we're transforming.
	 * This becomes the dcterms:subject field with an xsi:type of dcterms:DDC at the work FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process092(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dcterms:subject with a xsi:type of dcterms:DDC based on the 092 $a values
		return processFieldBasic(transformMe, transformInto, "092", 'a', "subject", AggregateXCRecord.DCTERMS_NAMESPACE, new Attribute("type", "dcterms:DDC", AggregateXCRecord.XSI_NAMESPACE), FrbrLevel.WORK);
	}

	/**
	 * Processes the 100 field from the MarcXmlRecord we're transforming.
	 * The abcdegq subfields become the xc:creator field at the work FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process100(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an xc:creator based on the 100 abcdegq values
		return processFieldWithAuthorityIgnoreRoles(transformMe, transformInto, "100", "abcdegq", "creator", AggregateXCRecord.XC_NAMESPACE, FrbrLevel.WORK);
	}

	/**
	 * Processes the 110 field from the MarcXmlRecord we're transforming.
	 * The abcdeg subfields become the xc:creator field at the work FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process110(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an xc:creator based on the 110 abcdeg values
		return processFieldWithAuthorityIgnoreRoles(transformMe, transformInto, "110", "abcdeg", "creator", AggregateXCRecord.XC_NAMESPACE, FrbrLevel.WORK);
	}

	/**
	 * Processes the 111 field from the MarcXmlRecord we're transforming.
	 * The acdegjq subfields become the xc:creator field at the work FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process111(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an xc:creator based on the 111 acdegjq values
		return processFieldWithAuthorityIgnoreRoles(transformMe, transformInto, "111", "acdegjnq", "creator", AggregateXCRecord.XC_NAMESPACE, FrbrLevel.WORK);
	}

	/**
	 * Processes the 130 field from the MarcXmlRecord we're transforming.
	 * The adfghklmnoprst0 subfields become both the rdvocab:workTitle field at the work FRBR level and
	 * the xc:expressionTitle field at the expression FRBR level
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	@SuppressWarnings("unchecked")
	protected AggregateXCRecord process130(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Get the elements with the requested tags in the MARC XML record
		List<Element> elements = transformMe.getDataFields("130");

		// The subfields of the 130 datafield we're processing
		String targetTitleOfWorkBuilderSubfields = "adghkmnoprst";
		String targetTitleOfExpressionBuilderSubfields = "adfghklmnoprst";

		// If there were matching elements, return the unmodified XC record
		if(elements.size() == 0)
			return transformInto;

		// Add each subfield to the specified level with the specified tag and attribute
		for(Element element : elements)
		{
			// A StringBuilder to concat the values of all the subfields of the Element
			StringBuilder titleOfWorkBuilder = new StringBuilder();
			StringBuilder titleOfExpressionBuilder = new StringBuilder();

			// Get the subfields of the current element
			List<Element> subfields = element.getChildren("subfield", marcNamespace);

			// Iterate over the subfields, and append each one to the StringBuilder if it
			// is in the list of target subfields
			for(Element subfield : subfields)
			{
				// Get the subfield's code
				String subfieldCode = subfield.getAttribute("code").getValue();

				// If the code is $0, the field's value needs to be added as an authority element
				if(subfieldCode.equals("0"))
				{
					String valueOf0 = subfield.getText();

					// Check that the $0 is in the format we're expecting: "(<prefix>)<value>"
					// If it isn't, we can't build an authority Attribute, so return null
					if(!valueOf0.contains("(") || !valueOf0.contains(")"))
					{
						LOG.warn("Encountered a $0 subfield in a format which did not contain a prefix, so we can't build an authortiy Attribute for it.  Ignoring the malformed $0, which had a value of " + valueOf0);

						continue;
					}

					// Get the value and prefix of the control number
					String value = valueOf0.substring(valueOf0.indexOf(')') + 1);
					String prefix = valueOf0.substring(valueOf0.indexOf('(')+1, valueOf0.indexOf(')'));

					// If the prefix is "DLC" we should return an lcnaf attribute.
					// If it's the organization code we should return an xcauth attribute
					// Otherwise return null since we don't recognize the prefix
					if(prefix.equals("DLC"))
					{
						ArrayList<Attribute> atts = new ArrayList<Attribute>();
						atts.add(new Attribute("type", "lcnaf", AggregateXCRecord.XSI_NAMESPACE));
						getXCRecordService().addElement(transformInto, Constants.ELEMENT_IDENTIFIER_FOR_THE_WORK, "n" + value.trim(), AggregateXCRecord.RDVOCAB_NAMESPACE, atts, FrbrLevel.WORK);
					}
					else if(prefix.equals(getOrganizationCode()))
					{
						ArrayList<Attribute> atts = new ArrayList<Attribute>();
						atts.add(new Attribute("type", "xcauth"));
						getXCRecordService().addElement(transformInto, Constants.ELEMENT_IDENTIFIER_FOR_THE_WORK, value.trim(), AggregateXCRecord.RDVOCAB_NAMESPACE, atts, FrbrLevel.WORK);
					}
				}

				if(targetTitleOfWorkBuilderSubfields.contains(subfieldCode))
					titleOfWorkBuilder.append(subfield.getText() + " ");
				
				if(targetTitleOfExpressionBuilderSubfields.contains(subfieldCode))
					titleOfExpressionBuilder.append(subfield.getText() + " ");
			}

			// If any target fields were found
			if(titleOfWorkBuilder.length() > 0)
			{
				String value = titleOfWorkBuilder.substring(0, titleOfWorkBuilder.length()-1); // The value is everything except the last space

				transformInto = processFieldBasic(transformInto, value, Constants.ELEMENT_TITLE_OF_WORK, AggregateXCRecord.RDVOCAB_NAMESPACE, null, FrbrLevel.WORK);
				
			}
			
			// If any target fields were found
			if(titleOfExpressionBuilder.length() > 0)
			{
				String value = titleOfExpressionBuilder.substring(0, titleOfExpressionBuilder.length()-1); // The value is everything except the last space

				transformInto = processFieldBasic(transformInto, value, Constants.ELEMENT_TITLE_OF_EXPRESSION, AggregateXCRecord.XC_NAMESPACE, null, FrbrLevel.EXPRESSION);
			}
		}

		// Return the result
		return transformInto;

		// Process the authority information
		//transformInto = addElementForAuthority(transformMe, transformInto, "130", FrbrLevel.WORK);

		// Create an rdvocab:workTitle based on the 130 adfghklmnoprst0 values
		//transformInto = processFieldBasic(transformMe, transformInto, "130", "adfghklmnoprst", "workTitle", XCRecord.RDVOCAB_NAMESPACE, null, FrbrLevel.WORK);

		// Create an xc:expressionTitle based on the 130 adfghklmnoprst0 values
		//return processFieldBasic(transformMe, transformInto, "130", "adfghklmnoprst", "expressionTitle", XCRecord.XC_NAMESPACE, null, FrbrLevel.EXPRESSION);
	}

	/**
	 * Processes the 210 field from the MarcXmlRecord we're transforming.
	 * The $a and $b become the dcterms:alternative field at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process210(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dcterms:alternative based on the 210 $a and $b values
		return processFieldBasic(transformMe, transformInto, "210", "ab", "alternative", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 222 field from the MarcXmlRecord we're transforming.
	 * The $a and $b become the dcterms:alternative field at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process222(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dcterms:alternative based on the 222 $a and $b values
		return processFieldBasic(transformMe, transformInto, "222", "ab", "alternative", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 240 field from the MarcXmlRecord we're transforming.
	 * The $0 becomes the rdvocab:identifierForTheWork field at the work FRBR level.
	 * The adfghklmnoprs subfields become the rdvocab:workTitle field at the work FRBR level.
	 * and the xc:expressionTitle field at the expression FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	@SuppressWarnings("unchecked")
	protected AggregateXCRecord process240(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Get the elements with the requested tags in the MARC XML record
		List<Element> elements = transformMe.getDataFields("240");

		// The subfields of the 130 datafield we're processing
		String targetTitleOfWorkBuilderSubfields = "adghkmnoprs";
		String targetTitleOfExpressionBuilderSubfields = "adfghklmnoprs";

		// If there were matching elements, return the unmodified XC record
		if(elements.size() == 0)
			return transformInto;

		// Add each subfield to the specified level with the specified tag and attribute
		for(Element element : elements)
		{
			// A StringBuilder to concat the values of all the subfields of the Element
			StringBuilder titleOfWorkBuilder = new StringBuilder();
			StringBuilder titleOfExpressionBuilder = new StringBuilder();

			// Get the subfields of the current element
			List<Element> subfields = element.getChildren("subfield", marcNamespace);

			// Iterate over the subfields, and append each one to the StringBuilder if it
			// is in the list of target subfields
			for(Element subfield : subfields)
			{
				// Get the subfield's code
				String subfieldCode = subfield.getAttribute("code").getValue();

				// If the code is $0, the field's value needs to be added as an authority element
				if(subfieldCode.equals("0"))
				{
					String valueOf0 = subfield.getText();

					// Check that the $0 is in the format we're expecting: "(<prefix>)<value>"
					// If it isn't, we can't build an authority Attribute, so return null
					if(!valueOf0.contains("(") || !valueOf0.contains(")"))
					{
						LOG.warn("Encountered a $0 subfield in a format which did not contain a prefix, so we can't build an authortiy Attribute for it.  Ignoring the malformed $0, which had a value of " + valueOf0);

						continue;
					}

					// Get the value and prefix of the control number
					String value = valueOf0.substring(valueOf0.indexOf(')') + 1);
					String prefix = valueOf0.substring(valueOf0.indexOf('(')+1, valueOf0.indexOf(')'));

					// If the prefix is "DLC" we should return an lcnaf attribute.
					// If it's the organization code we should return an xcauth attribute
					// Otherwise return null since we don't recognize the prefix
					if(prefix.equals("DLC"))
					{
						ArrayList<Attribute> atts = new ArrayList<Attribute>();
						atts.add(new Attribute("type", "lcnaf", AggregateXCRecord.XSI_NAMESPACE));
						getXCRecordService().addElement(transformInto, Constants.ELEMENT_IDENTIFIER_FOR_THE_WORK, "n" + value.trim(), AggregateXCRecord.RDVOCAB_NAMESPACE, atts, FrbrLevel.WORK);
					}
					else if(prefix.equals(getOrganizationCode()))
					{
						ArrayList<Attribute> atts = new ArrayList<Attribute>();
						atts.add(new Attribute("type", "xcauth"));
						getXCRecordService().addElement(transformInto, Constants.ELEMENT_IDENTIFIER_FOR_THE_WORK, value.trim(), AggregateXCRecord.RDVOCAB_NAMESPACE, atts, FrbrLevel.WORK);
					}
				}

				if(targetTitleOfWorkBuilderSubfields.contains(subfieldCode)) {
					titleOfWorkBuilder.append(subfield.getText() + " ");
				}
				
				if(targetTitleOfExpressionBuilderSubfields.contains(subfieldCode)) {
					titleOfExpressionBuilder.append(subfield.getText() + " ");
				}

			}

			// If any target fields were found
			if(titleOfWorkBuilder.length() > 0)
			{
				String value = titleOfWorkBuilder.substring(0, titleOfWorkBuilder.length()-1); // The value is everything except the last space

				transformInto = processFieldBasic(transformInto, value, Constants.ELEMENT_TITLE_OF_WORK, AggregateXCRecord.RDVOCAB_NAMESPACE, null, FrbrLevel.WORK);
				
			}

			// If any target fields were found
			if(titleOfExpressionBuilder.length() > 0)
			{
				String value = titleOfExpressionBuilder.substring(0, titleOfExpressionBuilder.length()-1); // The value is everything except the last space
			
				transformInto = processFieldBasic(transformInto, value, Constants.ELEMENT_TITLE_OF_EXPRESSION, AggregateXCRecord.XC_NAMESPACE, null, FrbrLevel.EXPRESSION);
			}
		}

		// Return the result
		return transformInto;

		// Process the authority information
		//transformInto = addElementForAuthority(transformMe, transformInto, "240", FrbrLevel.WORK);

		// Create an rdvocab:workTitle based on the 240 adfghklmnoprs values
		//transformInto = processFieldBasic(transformMe, transformInto, "240", "adfghklmnoprs", "workTitle", XCRecord.RDVOCAB_NAMESPACE, null, FrbrLevel.WORK);

		// Create an xc:expressionTitle based on the 240 adfghklmnoprs values
		//return processFieldBasic(transformMe, transformInto, "240", "adfghklmnoprs", "expressionTitle", XCRecord.XC_NAMESPACE, null, FrbrLevel.EXPRESSION);
	}

	/**
	 * Processes the 243 field from the MarcXmlRecord we're transforming.
	 * The $0 becomes the rdvocab:identifierForTheWork field at the work FRBR level.
	 * The adfghklmnoprs subfields become the rdvocab:workTitle field at the work FRBR level.
	 * and the xc:expressionTitle field at the expression FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	@SuppressWarnings("unchecked")
	protected AggregateXCRecord process243(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Get the elements with the requested tags in the MARC XML record
		List<Element> elements = transformMe.getDataFields("243");

		// The subfields of the 130 datafield we're processing
		String targetTitleOfWorkBuilderSubfields = "adghkmnoprs";
		String targetTitleOfExpressionBuilderSubfields = "adfghklmnoprs";

		// If there were matching elements, return the unmodified XC record
		if(elements.size() == 0)
			return transformInto;

		// Add each subfield to the specified level with the specified tag and attribute
		for(Element element : elements)
		{
			// A StringBuilder to concat the values of all the subfields of the Element
			StringBuilder titleOfWorkBuilder = new StringBuilder();
			StringBuilder titleOfExpressionBuilder = new StringBuilder();

			// Get the subfields of the current element
			List<Element> subfields = element.getChildren("subfield", marcNamespace);

			// Iterate over the subfields, and append each one to the StringBuilder if it
			// is in the list of target subfields
			for(Element subfield : subfields)
			{
				// Get the subfield's code
				String subfieldCode = subfield.getAttribute("code").getValue();

				// If the code is $0, the field's value needs to be added as an authority element
				if(subfieldCode.equals("0"))
				{
					String valueOf0 = subfield.getText();

					// Check that the $0 is in the format we're expecting: "(<prefix>)<value>"
					// If it isn't, we can't build an authority Attribute, so return null
					if(!valueOf0.contains("(") || !valueOf0.contains(")"))
					{
						LOG.warn("Encountered a $0 subfield in a format which did not contain a prefix, so we can't build an authortiy Attribute for it.  Ignoring the malformed $0, which had a value of " + valueOf0);

						continue;
					}

					// Get the value and prefix of the control number
					String value = valueOf0.substring(valueOf0.indexOf(')') + 1);
					String prefix = valueOf0.substring(valueOf0.indexOf('(')+1, valueOf0.indexOf(')'));

					// If the prefix is "DLC" we should return an lcnaf attribute.
					// If it's the organization code we should return an xcauth attribute
					// Otherwise return null since we don't recognize the prefix
					if(prefix.equals("DLC"))
					{
						ArrayList<Attribute> atts = new ArrayList<Attribute>();
						atts.add(new Attribute("type", "lcnaf", AggregateXCRecord.XSI_NAMESPACE));
						getXCRecordService().addElement(transformInto, Constants.ELEMENT_IDENTIFIER_FOR_THE_WORK, "n" + value.trim(), AggregateXCRecord.RDVOCAB_NAMESPACE, atts, FrbrLevel.WORK);
					}
					else if(prefix.equals(getOrganizationCode()))
					{
						ArrayList<Attribute> atts = new ArrayList<Attribute>();
						atts.add(new Attribute("type", "xcauth"));
						getXCRecordService().addElement(transformInto, Constants.ELEMENT_IDENTIFIER_FOR_THE_WORK, value.trim(), AggregateXCRecord.RDVOCAB_NAMESPACE, atts, FrbrLevel.WORK);
					}
				}

				if(targetTitleOfWorkBuilderSubfields.contains(subfieldCode))
					titleOfWorkBuilder.append(subfield.getText() + " ");
				
				if(targetTitleOfExpressionBuilderSubfields.contains(subfieldCode))
					titleOfExpressionBuilder.append(subfield.getText() + " ");

			}

			// If any target fields were found
			if(titleOfWorkBuilder.length() > 0)
			{
				String value = titleOfWorkBuilder.substring(0, titleOfWorkBuilder.length()-1); // The value is everything except the last space

				transformInto = processFieldBasic(transformInto, value, Constants.ELEMENT_TITLE_OF_WORK, AggregateXCRecord.RDVOCAB_NAMESPACE, null, FrbrLevel.WORK);
				
			}
			
			// If any target fields were found
			if(titleOfExpressionBuilder.length() > 0)
			{
				String value = titleOfExpressionBuilder.substring(0, titleOfExpressionBuilder.length()-1); // The value is everything except the last space

				transformInto = processFieldBasic(transformInto, value, Constants.ELEMENT_TITLE_OF_EXPRESSION, AggregateXCRecord.XC_NAMESPACE, null, FrbrLevel.EXPRESSION);
			}

		}

		// Return the result
		return transformInto;

		// Process the authority information
		//transformInto = addElementForAuthority(transformMe, transformInto, "243", FrbrLevel.WORK);

		// Create an rdvocab:workTitle based on the 243 adfghklmnoprs values
		//transformInto = processFieldBasic(transformMe, transformInto, "243", "adfghklmnoprs", "workTitle", XCRecord.RDVOCAB_NAMESPACE, null, FrbrLevel.WORK);

		// Create an xc:expressionTitle based on the 243 adfghklmnoprs values
		//return processFieldBasic(transformMe, transformInto, "243", "adfghklmnoprs", "expressionTitle", XCRecord.XC_NAMESPACE, null, FrbrLevel.EXPRESSION);
	}

	/**
	 * Processes the 245 field from the MarcXmlRecord we're transforming.
	 * The $c becomes the xc:titleAnnotation field at the manifestation FRBR level.
	 * The abfgknps subfields become the dc:title field at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process245(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an xc:titleAnnotation based on the 245 $c values
		transformInto = processFieldBasic(transformMe, transformInto, "245", 'c', "statementOfResponsibilityRelatingToTitle", AggregateXCRecord.RDVOCAB_NAMESPACE, null, FrbrLevel.MANIFESTATION);

		// Create an dc:title based on the 245 abfgknps values
		return processFieldBasic(transformMe, transformInto, "245", "abfgknps", "title", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 246 field from the MarcXmlRecord we're transforming.
	 * The abfnp subfields become the dcterms:alternative field at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	@SuppressWarnings("unchecked")
	protected AggregateXCRecord process246(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Get the elements with the requested field
		List<Element> elements = transformMe.getDataFields("246");

		// The subfields we're processing
		String targetSubfields = "abfnp";

		// If there were no matching elements, return the unmodified XC record
		if(elements.size() == 0)
			return transformInto;

		// Add each subfield to the specified level with the specified tag and attribute
		for(Element element : elements)
		{
			// Get the required indicator
			String ind2 = MarcXmlRecord.getIndicatorOfField(element, "2");

			// Setup the attribute list for the processed field
			ArrayList<Attribute> attributes = new ArrayList<Attribute>();

			// A StringBuilder to concat the values of all the subfields of the Element
			StringBuilder builder = new StringBuilder();

			// Get the subfields of the current element
			List<Element> subfields = element.getChildren("subfield", marcNamespace);

			// Iterate over the subfields, and append each one to the StringBuilder if it
			// is in the list of target subfields
			for(Element subfield : subfields)
			{
				// Get the subfield's code
				String subfieldCode = subfield.getAttribute("code").getValue();

				if(targetSubfields.contains(subfieldCode))
					builder.append(subfield.getText() + " ");
			}

			// If any target fields were found
			if(builder.length() > 0)
			{
				String value = builder.substring(0, builder.length()-1); // The value is everything except the last space

				// If the 2nd indicator is 1,
				if(ind2 != null && ind2.equals("1"))
				{
					if(LOG.isDebugEnabled())
						LOG.debug("Adding a " + FrbrLevel.MANIFESTATION + " level title based on the concatination of the 246's subfields' value, which is " + value);

					// Create an dc:title based on the 246 abfnp values
					getXCRecordService().addElement(transformInto, "title", value.trim(), AggregateXCRecord.DCTERMS_NAMESPACE, attributes, FrbrLevel.MANIFESTATION);
				}
				else
				{
					if(LOG.isDebugEnabled())
						LOG.debug("Adding a " + FrbrLevel.MANIFESTATION + " level alternative based on the concatination of the 246's subfields' value, which is " + value);

					// Create a dcterms:alternative based on the 246 abfnp values
					getXCRecordService().addElement(transformInto, "alternative", value.trim(), AggregateXCRecord.DCTERMS_NAMESPACE, attributes, FrbrLevel.MANIFESTATION);
				}
			}
		}

		// Return the result
		return transformInto;
	}

	/**
	 * Processes the 247 field from the MarcXmlRecord we're transforming.
	 * The abfnp subfields become the dcterms:alternative field at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process247(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dcterms:alternative based on the 247 abnfp values
		return processFieldBasic(transformMe, transformInto, "247", "abfnp", "alternative", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 250 field from the MarcXmlRecord we're transforming.
	 * The ab subfields become the rdvocab:edition field at the manifestation FRBR level.
	 * The $a subfield becomes the rdvocab:version field at the expression FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process250(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an rdvocab:edition based on the 250 ab values
		transformInto = processFieldBasic(transformMe, transformInto, "250", "ab", "editionStatement", AggregateXCRecord.RDVOCAB_NAMESPACE, null, FrbrLevel.MANIFESTATION);

		// Create an rdvocab:version based on the 250 $a value
		return processFieldBasic(transformMe, transformInto, "250", 'a', "version", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.EXPRESSION);
	}

	/**
	 * Processes the 254 field from the MarcXmlRecord we're transforming.
	 * The $a subfields become the rdvocab:edition field at the manifestation FRBR level.
	 * The $a subfield becomes the rdvocab:version field at the expression FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process254(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Get the target subfields MARC XML record
		List<String> subfields = transformMe.getSubfield("254", 'a');

		// If there were no matching subfields return the unmodified XC record
		if(subfields == null || subfields.size() == 0)
			return transformInto;

		// Add each subfield to the specified level with the specified tag and attribute
		for(String value : subfields)
		{
			transformInto = processFieldBasic(transformInto, value, "editionStatement", AggregateXCRecord.RDVOCAB_NAMESPACE, null, FrbrLevel.MANIFESTATION);
			transformInto = processFieldBasic(transformInto, value, "version", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.EXPRESSION);
		}

		// Return the result
		return transformInto;

		// Create an rdvocab:edition based on the 254 $a value
		//transformInto = processFieldBasic(transformMe, transformInto, "254", 'a', "editionStatement", XCRecord.RDVOCAB_NAMESPACE, null, FrbrLevel.MANIFESTATION);

		// Create an rdvocab:version based on the 254 $a value
		//return processFieldBasic(transformMe, transformInto, "254", 'a', "version", XCRecord.RDVOCAB_NAMESPACE, null, FrbrLevel.EXPRESSION);
	}

	/**
	 * Processes the 255 field from the MarcXmlRecord we're transforming.
	 * The abcdefg subfields become the rdvocab:scale field at the expression FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process255(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an rdvocab:scale based on the 255 abcdefg values
		return processFieldBasic(transformMe, transformInto, "255", "abcdefg", "scale", AggregateXCRecord.RDVOCAB_NAMESPACE, null, FrbrLevel.EXPRESSION);
	}

	/**
	 * Processes the 260 field from the MarcXmlRecord we're transforming.
	 * The $a and $e become rdvocab:placeOfProduction fields at the manifestation FRBR level.
	 * The $b and $f become dc:publisher fields at the manifestation FRBR level.
	 * The $c and $g become dcterms:issued fields at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process260(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an rdvocab:placeOfProduction based on the 260 $a values
		transformInto = processFieldBasic(transformMe, transformInto, "260", 'a', "placeOfProduction", AggregateXCRecord.RDVOCAB_NAMESPACE, null, FrbrLevel.MANIFESTATION);

		// Create an dc:publisher based on the 260 $b values
		transformInto = processFieldBasic(transformMe, transformInto, "260", 'b', "publisher", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.MANIFESTATION);

		// Create an dcterms:issued based on the 260 $c values
		transformInto = processFieldBasic(transformMe, transformInto, "260", 'c', "issued", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.MANIFESTATION);

		// Create an rdvocab:placeOfProduction based on the 260 $e values
		transformInto = processFieldBasic(transformMe, transformInto, "260", 'e', "placeOfProduction", AggregateXCRecord.RDVOCAB_NAMESPACE, null, FrbrLevel.MANIFESTATION);

		// Create an dc:publisher  based on the 260 $f values
		transformInto = processFieldBasic(transformMe, transformInto, "260", 'f', "publisher", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.MANIFESTATION);

		// Create an dcterms:issued based on the 260 $g values
		return processFieldBasic(transformMe, transformInto, "260", 'g', "issued", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 300 field from the MarcXmlRecord we're transforming.
	 * The $a subfields become dcterms:extent fields at the manifestation FRBR level.
	 * The $b subfields become rdvocab:illustration fields at the expression FRBR level.
	 * The $c subfields become rdvocab:dimensions fields at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process300(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dcterms:extent based on the 300 $a values
		transformInto = processFieldBasic(transformMe, transformInto, "300", 'a', "extent", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.MANIFESTATION);

		// Create a different element depending on the leader06 value based on the 300 $b values
		char leader06 = transformMe.getLeader().charAt(6);
		if("ij".contains("" + leader06))
			transformInto = processFieldBasic(transformMe, transformInto, "300", 'b', "soundCharacteristics", AggregateXCRecord.RDVOCAB_NAMESPACE, null, FrbrLevel.MANIFESTATION);
		else if("acdt".contains("" + leader06))
			transformInto = processFieldBasic(transformMe, transformInto, "300", 'b', "illustrationContent", AggregateXCRecord.RDVOCAB_NAMESPACE, null, FrbrLevel.EXPRESSION);
		else
			transformInto = processFieldBasic(transformMe, transformInto, "300", 'b', "otherPhysicalDetails", AggregateXCRecord.XC_NAMESPACE, null, FrbrLevel.MANIFESTATION);

		// Create an rdvocab:dimensions based on the 300 $c values
		transformInto = processFieldBasic(transformMe, transformInto, "300", 'c', "dimensions", AggregateXCRecord.RDVOCAB_NAMESPACE, null, FrbrLevel.MANIFESTATION);
		 
		return processFieldBasic(transformMe, transformInto, "300", 'e', "hasPart", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 310 field from the MarcXmlRecord we're transforming.
	 * The ab subfields become the rdvocab:frequency field at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process310(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an rdvocab:frequency based on the 310 ab values
		return processFieldBasic(transformMe, transformInto, "310", "ab", "frequency", AggregateXCRecord.RDVOCAB_NAMESPACE, null, FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 321 field from the MarcXmlRecord we're transforming.
	 * The ab subfields become the rdvocab:frequency field at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process321(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an rdvocab:frequency based on the 321 ab values
		return processFieldBasic(transformMe, transformInto, "321", "ab", "frequency", AggregateXCRecord.RDVOCAB_NAMESPACE, null, FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 362 field from the MarcXmlRecord we're transforming.
	 * The $a and $z become the rdvocab:numbering field at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process362(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an rdvocab:numbering based on the 362 $a and $z values
		return processFieldBasic(transformMe, transformInto, "362", "az", "numberingOfSerials", AggregateXCRecord.RDVOCAB_NAMESPACE, null, FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 440 field from the MarcXmlRecord we're transforming.
	 * The anpv subfields become the xc:isPartOf field at the work FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process440(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an xc:isPartOf based on the 440 anpv0 values
		return processFieldWithAuthority(transformMe, transformInto, "440", "anpv", "isPartOf", AggregateXCRecord.XC_NAMESPACE, FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 490 field from the MarcXmlRecord we're transforming.
	 * The av subfields become the dcterms:isPartOf field at the manifestation FRBR level.
	 * The x subfield becomes the dcterms:ISSN attribute on the field.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process490(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create the subfield to attribute map.
		// We'll map the $x subfield to a dcterms:ISSN attribute
		HashMap<String, Attribute> subfieldToAttribute = new HashMap<String, Attribute>();
		subfieldToAttribute.put("x", new Attribute("ISSN", "null", AggregateXCRecord.DCTERMS_NAMESPACE));

		// Create an dcterms:isPartOf based on the 490 av values
		return processFieldReqIndicatorAttFromField(transformMe, transformInto, "490", "av", "isPartOf", AggregateXCRecord.DCTERMS_NAMESPACE, "1", "0", subfieldToAttribute, FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 500 field from the MarcXmlRecord we're transforming.
	 * The a3 subfields become the dc:description field at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process500(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dc:description based on the 500 a3 values
		return processFieldBasic(transformMe, transformInto, "500", "a3", "description", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 501 field from the MarcXmlRecord we're transforming.
	 * The $a subfields become dc:relation fields at the expression FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process501(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dc:relation based on the 501 $a values
		return processFieldBasic(transformMe, transformInto, "501", 'a', "relation", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.EXPRESSION);
	}

	/**
	 * Processes the 502 field from the MarcXmlRecord we're transforming.
	 * The $a subfields become rdvocab:academicDegree fields at the work FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process502(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an rdvocab:dissertationOrThesisInformation based on the 502 $a values
		return processFieldBasic(transformMe, transformInto, "502", 'a', "dissertationOrThesisInformation", AggregateXCRecord.RDVOCAB_NAMESPACE, null, FrbrLevel.WORK);
	}

	/**
	 * Processes the 504 field from the MarcXmlRecord we're transforming.
	 * The $a and $b become the dc:description field at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process504(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dc:description based on the 504 $a and $b values
		return processFieldBasic(transformMe, transformInto, "504", "ab", "description", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 505 field from the MarcXmlRecord we're transforming.
	 * The agrtu subfields become dcterms:tableOfContents fields at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process505(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dcterms:tableOfContents based on the 505 agrtu values
		return processFieldBasic(transformMe, transformInto, "505", "agrtu", "tableOfContents", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 506 field from the MarcXmlRecord we're transforming.
	 * The abcdefu3 subfields become the dc:rights field at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process506(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dc:rights based on the 506 abcdefu3 values
		return processFieldBasic(transformMe, transformInto, "506", "abcdefu3", "rights", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 507 field from the MarcXmlRecord we're transforming.
	 * The $a and $b become the dc:scale field at the expression FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process507(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dc:scale based on the 507 $a and $b values
		return processFieldBasic(transformMe, transformInto, "507", "ab", "scale", AggregateXCRecord.RDVOCAB_NAMESPACE, null, FrbrLevel.EXPRESSION);
	}

	/**
	 * Processes the 508 field from the MarcXmlRecord we're transforming.
	 * The $a subfields become rdvocab:artisticCredits fields at the expression FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process508(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an rdvocab:artisticAndOrTechnicalCredits based on the 508 $a values
		return processFieldBasic(transformMe, transformInto, "508", 'a', "artisticAndOrTechnicalCredits", AggregateXCRecord.RDVOCAB_NAMESPACE, null, FrbrLevel.EXPRESSION);
	}

	/**
	 * Processes the 510 field from the MarcXmlRecord we're transforming.
	 * The abc3 subfields become the dcterms:isReferencedBy field with a dcterms:ISSN equal to the 510's $x value at the expression FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process510(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dcterms:isReferencedBy based on the 510 abc3 values with a dcterms:ISSN based on the corrosponding 510 $x value
		return processFieldAttributeFromSubfield(transformMe, transformInto, "510", "abc3", "isReferencedBy", AggregateXCRecord.DCTERMS_NAMESPACE, new Attribute("ISSN", "null", AggregateXCRecord.DCTERMS_NAMESPACE), 'x', null, FrbrLevel.EXPRESSION);
	}

	/**
	 * Processes the 511 field from the MarcXmlRecord we're transforming.
	 * The $a subfields become rdarole:performer fields at the expression FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process511(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an rdarole:performerNarratorAndOrPresenter based on the 511 $a values
		return processFieldBasic(transformMe, transformInto, "511", 'a', "performerNarratorAndOrPresenter", AggregateXCRecord.RDVOCAB_NAMESPACE, null, FrbrLevel.EXPRESSION);
	}

	/**
	 * Processes the 513 field from the MarcXmlRecord we're transforming.
	 * The $a and $b become the dcterms:temporal field at the work FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process513(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dcterms:temporal based on the 513 $a and $b values
		return processFieldBasic(transformMe, transformInto, "513", "ab", "temporal", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.WORK);
	}

	/**
	 * Processes the 515 field from the MarcXmlRecord we're transforming.
	 * The $a becomes the rdvocab:numbering field at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process515(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an rdvocab:numberingOfSerials based on the 515 $a values
		return processFieldBasic(transformMe, transformInto, "515", 'a', "numberingOfSerials", AggregateXCRecord.RDVOCAB_NAMESPACE, null, FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 518 field from the MarcXmlRecord we're transforming.
	 * The a3 subfields become the rdvocab:captureDetail field at the expression FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process518(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an rdvocab:placeAndDateOfCapture based on the 518 a3 values
		return processFieldBasic(transformMe, transformInto, "518", "a3", "placeAndDateOfCapture", AggregateXCRecord.RDVOCAB_NAMESPACE, null, FrbrLevel.EXPRESSION);
	}

	/**
	 * Processes the 520 field from the MarcXmlRecord we're transforming.
	 * The abcu3 subfields become the dcterms:abstract field at the work FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process520(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dcterms:abstract based on the 520 abcu3 values
		return processFieldBasic(transformMe, transformInto, "520", "abcu3", "abstract", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.WORK);
	}

	/**
	 * Processes the 521 field from the MarcXmlRecord we're transforming.
	 * The ab3 subfields become the dcterms:audience field at the work FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process521(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dcterms:audience based on the 521 ab3 values
		return processFieldBasic(transformMe, transformInto, "521", "ab3", "audience", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.WORK);
	}

	/**
	 * Processes the 522 field from the MarcXmlRecord we're transforming.
	 * The $a becomes the dcterms:spatial field at the work FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process522(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dcterms:spatial based on the 522 $a values
		return processFieldBasic(transformMe, transformInto, "522", 'a', "spatial", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.WORK);
	}

	/**
	 * Processes the 525 field from the MarcXmlRecord we're transforming.
	 * This becomes the xc:relation field at the work FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process525(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an xc:relation based on the 525 $a values
		return processFieldBasic(transformMe, transformInto, "525", 'a', "relation", AggregateXCRecord.XC_NAMESPACE, null, FrbrLevel.WORK);
	}

	/**
	 * Processes the 530 field from the MarcXmlRecord we're transforming.
	 * The abcdu3 subfields become the dcterms:hasFormat field at the expression FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process530(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dcterms:hasFormat based on the 530 abcdu3 values
		return processFieldBasic(transformMe, transformInto, "530", "abcdu3", "hasFormat", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.EXPRESSION);
	}

	/**
	 * Processes the 533 field from the MarcXmlRecord we're transforming.
	 * The abcdefmn3 subfields become the dcterms:hasFormat field at the expression FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process533(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dcterms:hasFormat based on the 533 abcdefmn3 values
		return processFieldBasic(transformMe, transformInto, "533", "abcdefmn3", "hasFormat", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.EXPRESSION);
	}

	/**
	 * Processes the 534 field from the MarcXmlRecord we're transforming.
	 * The abc3 subfields become the dcterms:isFormatOf field with a dcterms:ISSN equal to the 510's $x value at the expression FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process534(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dcterms:isFormatOf based on the 534 abc3 values with a dcterms:ISSN based on the corrosponding 534 $x value
		return processFieldAttributeFromSubfield(transformMe, transformInto, "534", "abcefklmnptx3", "isFormatOf", AggregateXCRecord.DCTERMS_NAMESPACE, new Attribute("ISSN", "null", AggregateXCRecord.DCTERMS_NAMESPACE), 'x', null, FrbrLevel.EXPRESSION);
	}

	/**
	 * Processes the 538 field from the MarcXmlRecord we're transforming.
	 * The aiu3 subfields become the dcterms:requires field at the expression FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process538(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dcterms:requires based on the 538 aiu3 values
		return processFieldBasic(transformMe, transformInto, "538", "aiu3", "requires", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.EXPRESSION);
	}

	/**
	 * Processes the 540 field from the MarcXmlRecord we're transforming.
	 * The abcdu3 subfields become the dc:rights field at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process540(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dc:rights based on the 540 abcdu3 values
		return processFieldBasic(transformMe, transformInto, "540", "abcdu3", "rights", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 544 field from the MarcXmlRecord we're transforming.
	 * The abcden3 subfields become the dc:description field at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process544(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dc:description based on the 544 abcden3 values
		return processFieldBasic(transformMe, transformInto, "544", "abcden3", "description", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 546 field from the MarcXmlRecord we're transforming.
	 * The ab3 subfields become the dcterms:language field at the expression FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process546(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dcterms:language based on the 546 ab3 values
		return processFieldBasic(transformMe, transformInto, "546", "ab3", "description", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 547 field from the MarcXmlRecord we're transforming.
	 * The $a subfields become dc:description fields at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process547(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dc:description based on the 547 $a values
		return processFieldBasic(transformMe, transformInto, "547", 'a', "description", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 550 field from the MarcXmlRecord we're transforming.
	 * This becomes the dc:description field at the expression FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process550(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dc:description based on the 550 $a values
		return processFieldBasic(transformMe, transformInto, "550", 'a', "description", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.EXPRESSION);
	}

	/**
	 * Processes the 555 field from the MarcXmlRecord we're transforming.
	 * If the leader 08 value is not 'a' no processing will occur for this field
	 * Otherwise the abcdu3 subfields become the dc:description field at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process555(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Return the unmodified record if the leader 08 value is not 'a'
		if(transformMe.getLeader().charAt(8) != 'a')
			return transformInto;

		// Create an dc:description based on the 555 abcdu3 values
		return processFieldBasic(transformMe, transformInto, "555", "abcdu3", "description", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 580 field from the MarcXmlRecord we're transforming.
	 * The $a subfields become dc:relation fields at the expression FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process580(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dc:relation based on the 580 $a values
		return processFieldBasic(transformMe, transformInto, "580", 'a', "relation", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.EXPRESSION);
	}

	/**
	 * Processes the 586 field from the MarcXmlRecord we're transforming.
	 * The a3 subfields become the rdvocab:awards field at the expression FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process586(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an rdvocab:awards based on the 586 a3 values
		return processFieldBasic(transformMe, transformInto, "586", "a3", "awards", AggregateXCRecord.RDVOCAB_NAMESPACE, null, FrbrLevel.EXPRESSION);
	}

	/**
	 * Processes the 59X field from the MarcXmlRecord we're transforming.
	 * The $a subfields become dc:description fields at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process59X(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dc:description based on the 590 $a values
		transformInto = processFieldBasic(transformMe, transformInto, "590", 'a', "description", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.MANIFESTATION);

		// Create an dc:description based on the 591 $a values
		transformInto = processFieldBasic(transformMe, transformInto, "591", 'a', "description", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.MANIFESTATION);

		// Create an dc:description based on the 592 $a values
		transformInto = processFieldBasic(transformMe, transformInto, "592", 'a', "description", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.MANIFESTATION);

		// Create an dc:description based on the 593 $a values
		transformInto = processFieldBasic(transformMe, transformInto, "593", 'a', "description", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.MANIFESTATION);

		// Create an dc:description based on the 594 $a values
		transformInto = processFieldBasic(transformMe, transformInto, "594", 'a', "description", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.MANIFESTATION);

		// Create an dc:description based on the 595 $a values
		transformInto = processFieldBasic(transformMe, transformInto, "595", 'a', "description", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.MANIFESTATION);

		// Create an dc:description based on the 596 $a values
		transformInto = processFieldBasic(transformMe, transformInto, "596", 'a', "description", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.MANIFESTATION);

		// Create an dc:description based on the 597 $a values
		transformInto = processFieldBasic(transformMe, transformInto, "597", 'a', "description", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.MANIFESTATION);

		// Create an dc:description based on the 598 $a values
		transformInto = processFieldBasic(transformMe, transformInto, "598", 'a', "description", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.MANIFESTATION);

		// Create an dc:description based on the 599 $a values
		return processFieldBasic(transformMe, transformInto, "599", 'a', "description", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 600 field from the MarcXmlRecord we're transforming.
	 * This becomes the xc:subject field with a type based on the 2nd indicator at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process600(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Setup the map from the 2nd indicator to the type of the xc:subject
		HashMap<String, Attribute> indicatorToType = new HashMap<String, Attribute>();
		indicatorToType.put("0", new Attribute("type", "dcterms:LCSH", AggregateXCRecord.XSI_NAMESPACE));
		indicatorToType.put("1", new Attribute("type", "lcac"));
		indicatorToType.put("2", new Attribute("type", "dcterms:MESH", AggregateXCRecord.XSI_NAMESPACE));
		indicatorToType.put("3", new Attribute("type", "nal"));
		indicatorToType.put("5", new Attribute("type", "cash"));
		indicatorToType.put("6", new Attribute("type", "rvm"));
		indicatorToType.put("7", new Attribute("type", "$2"));

		// Create an xc:subject with a type based on the 2nd indicator and a value based on the 600 abcdefgklmnopqrstuvwxyz23 values
		return processFieldWithAuthorityAttributeFromIndicator(transformMe, transformInto, "600", "abcdefgklmnopqrstuvwxyz23", "vxyz", "subject", AggregateXCRecord.XC_NAMESPACE, "2", indicatorToType, FrbrLevel.WORK);
	}

	/**
	 * Processes the 610 field from the MarcXmlRecord we're transforming.
	 * This becomes the xc:subject field with a type based on the 2nd indicator at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process610(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Setup the map from the 2nd indicator to the type of the xc:subject
		HashMap<String, Attribute> indicatorToType = new HashMap<String, Attribute>();
		indicatorToType.put("0", new Attribute("type", "dcterms:LCSH", AggregateXCRecord.XSI_NAMESPACE));
		indicatorToType.put("1", new Attribute("type", "lcac"));
		indicatorToType.put("2", new Attribute("type", "dcterms:MESH", AggregateXCRecord.XSI_NAMESPACE));
		indicatorToType.put("3", new Attribute("type", "nal"));
		indicatorToType.put("5", new Attribute("type", "cash"));
		indicatorToType.put("6", new Attribute("type", "rvm"));
		indicatorToType.put("7", new Attribute("type", "$2"));

		// Create an xc:subject with a type based on the 2nd indicator and a value based on the 610 abcdefgklmnopqrstuvwxyz23 values
		return processFieldWithAuthorityAttributeFromIndicator(transformMe, transformInto, "610", "abcdefgklmnopqrstuvwxyz234", "vxyz", "subject", AggregateXCRecord.XC_NAMESPACE, "2", indicatorToType, FrbrLevel.WORK);
	}

	/**
	 * Processes the 611 field from the MarcXmlRecord we're transforming.
	 * This becomes the xc:subject field with a type based on the 2nd indicator at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process611(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Setup the map from the 2nd indicator to the type of the xc:subject
		HashMap<String, Attribute> indicatorToType = new HashMap<String, Attribute>();
		indicatorToType.put("0", new Attribute("type", "dcterms:LCSH", AggregateXCRecord.XSI_NAMESPACE));
		indicatorToType.put("1", new Attribute("type", "lcac"));
		indicatorToType.put("2", new Attribute("type", "dcterms:MESH", AggregateXCRecord.XSI_NAMESPACE));
		indicatorToType.put("3", new Attribute("type", "nal"));
		indicatorToType.put("5", new Attribute("type", "cash"));
		indicatorToType.put("6", new Attribute("type", "rvm"));
		indicatorToType.put("7", new Attribute("type", "$2"));

		// Create an xc:subject with a type based on the 2nd indicator and a value based on the 611 acdefgklnpqstvwxyz234 values
		return processFieldWithAuthorityAttributeFromIndicator(transformMe, transformInto, "611", "acdefgklnpqstvwxyz234", "vxyz", "subject", AggregateXCRecord.XC_NAMESPACE, "2", indicatorToType, FrbrLevel.WORK);
	}

	/**
	 * Processes the 630 field from the MarcXmlRecord we're transforming.
	 * This becomes the xc:subject field with a type based on the 2nd indicator at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process630(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Setup the map from the 2nd indicator to the type of the xc:subject
		HashMap<String, Attribute> indicatorToType = new HashMap<String, Attribute>();
		indicatorToType.put("0", new Attribute("type", "dcterms:LCSH", AggregateXCRecord.XSI_NAMESPACE));
		indicatorToType.put("1", new Attribute("type", "lcac"));
		indicatorToType.put("2", new Attribute("type", "dcterms:MESH", AggregateXCRecord.XSI_NAMESPACE));
		indicatorToType.put("3", new Attribute("type", "nal"));
		indicatorToType.put("5", new Attribute("type", "cash"));
		indicatorToType.put("6", new Attribute("type", "rvm"));
		indicatorToType.put("7", new Attribute("type", "$2"));

		// Create an xc:subject with a type based on the 2nd indicator and a value based on the 630 adefgklmnoprstvwxyz234 values
		return processFieldWithAuthorityAttributeFromIndicator(transformMe, transformInto, "630", "adefgklmnoprstvwxyz234", "vxyz", "subject", AggregateXCRecord.XC_NAMESPACE, "2", indicatorToType, FrbrLevel.WORK);
	}

	/**
	 * Processes the 648 field from the MarcXmlRecord we're transforming.
	 * This becomes the xc:subject field with a type based on the 2nd indicator at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process648(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Setup the map from the 2nd indicator to the type of the xc:subject
		HashMap<String, Attribute> indicatorToType = new HashMap<String, Attribute>();
		indicatorToType.put("0", new Attribute("type", "dcterms:LCSH", AggregateXCRecord.XSI_NAMESPACE));
		indicatorToType.put("1", new Attribute("type", "lcac"));
		indicatorToType.put("2", new Attribute("type", "dcterms:MESH", AggregateXCRecord.XSI_NAMESPACE));
		indicatorToType.put("3", new Attribute("type", "nal"));
		indicatorToType.put("5", new Attribute("type", "cash"));
		indicatorToType.put("6", new Attribute("type", "rvm"));
		indicatorToType.put("7", new Attribute("type", "$2"));

		// Create an xc:temporal with a type based on the 2nd indicator and a value based on the 648 avwxyz2 values
		return processFieldWithAuthorityAttributeFromIndicator(transformMe, transformInto, "648", "avwxyz", "vxyz", "temporal", AggregateXCRecord.XC_NAMESPACE, "2", indicatorToType, FrbrLevel.WORK);
	}

	/**
	 * Processes the 650 field from the MarcXmlRecord we're transforming.
	 * This becomes the xc:subject field with a type based on the 2nd indicator at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process650(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Setup the map from the 2nd indicator to the type of the xc:subject
		HashMap<String, Attribute> indicatorToType = new HashMap<String, Attribute>();
		indicatorToType.put("0", new Attribute("type", "dcterms:LCSH", AggregateXCRecord.XSI_NAMESPACE));
		indicatorToType.put("1", new Attribute("type", "lcac"));
		indicatorToType.put("2", new Attribute("type", "dcterms:MESH", AggregateXCRecord.XSI_NAMESPACE));
		indicatorToType.put("3", new Attribute("type", "nal"));
		indicatorToType.put("5", new Attribute("type", "cash"));
		indicatorToType.put("6", new Attribute("type", "rvm"));
		indicatorToType.put("7", new Attribute("type", "$2"));

		// Create an xc:subject with a type based on the 2nd indicator and a value based on the 650 abcdevxyz234 values
		return processFieldWithAuthorityAttributeFromIndicator(transformMe, transformInto, "650", "abcdevxyz234", "vxyz", "subject", AggregateXCRecord.XC_NAMESPACE, "2", indicatorToType, FrbrLevel.WORK);
	}

	/**
	 * Processes the 651 field from the MarcXmlRecord we're transforming.
	 * This becomes the xc:subject field with a type based on the 2nd indicator at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process651(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Setup the map from the 2nd indicator to the type of the xc:subject
		HashMap<String, Attribute> indicatorToType = new HashMap<String, Attribute>();
		indicatorToType.put("0", new Attribute("type", "dcterms:LCSH", AggregateXCRecord.XSI_NAMESPACE));
		indicatorToType.put("1", new Attribute("type", "lcac"));
		indicatorToType.put("2", new Attribute("type", "dcterms:MESH", AggregateXCRecord.XSI_NAMESPACE));
		indicatorToType.put("3", new Attribute("type", "nal"));
		indicatorToType.put("5", new Attribute("type", "cash"));
		indicatorToType.put("6", new Attribute("type", "rvm"));
		indicatorToType.put("7", new Attribute("type", "$2"));

		// Create an xc:spatial with a type based on the 2nd indicator and a value based on the 651 aevxyz234 values
		return processFieldWithAuthorityAttributeFromIndicator(transformMe, transformInto, "651", "aevxyz234", "vxyz", "spatial", AggregateXCRecord.XC_NAMESPACE, "2", indicatorToType, FrbrLevel.WORK);
	}

	/**
	 * Processes the 653 field from the MarcXmlRecord we're transforming.
	 * This becomes the dcterms:subject field with a type of LCCN at the work FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process653(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dcterms:subject based on the 653 $a values
		return processFieldBasic(transformMe, transformInto, "653", 'a', "subject", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.WORK);
	}

	/**
	 * Processes the 654 field from the MarcXmlRecord we're transforming.
	 * The abcevyz034 subfields become the dcterms:subject field at the work FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process654(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dcterms:subject based on the 654 abcevyz034 values
		return processFieldAttributeFromSubfield(transformMe, transformInto, "654", "abcevyz034", "bcevyz034", "subject", AggregateXCRecord.DCTERMS_NAMESPACE, new Attribute("type", "null"), '2', null, FrbrLevel.WORK);
	}

	/**
	 * Processes the 655 field from the MarcXmlRecord we're transforming.
	 * This becomes the xc:subject field with a type based on the 2nd indicator at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process655(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Setup the map from the 2nd indicator to the type of the xc:subject
		HashMap<String, Attribute> indicatorToType = new HashMap<String, Attribute>();
		indicatorToType.put("0", new Attribute("type", "dcterms:LCSH", AggregateXCRecord.XSI_NAMESPACE));
		indicatorToType.put("1", new Attribute("type", "lcac"));
		indicatorToType.put("2", new Attribute("type", "dcterms:MESH", AggregateXCRecord.XSI_NAMESPACE));
		indicatorToType.put("3", new Attribute("type", "nal"));
		indicatorToType.put("5", new Attribute("type", "cash"));
		indicatorToType.put("6", new Attribute("type", "rvm"));
		indicatorToType.put("7", new Attribute("type", "$2"));

		// Create an xc:type with a type based on the 2nd indicator and a value based on the 655 abcvwyxz23 values
		return processFieldWithAuthorityAttributeFromIndicator(transformMe, transformInto, "655", "abcvwxyz3", "vxyz", "type", AggregateXCRecord.XC_NAMESPACE, "2", indicatorToType, FrbrLevel.WORK);
	}

	/**
	 * Processes the 700 field from the MarcXmlRecord we're transforming.
	 * For each 700 element without a $t subfield, the abcdegq4 subfields become the expression/xc:contributor, and the $0 becomes the agentID authority attribute.
	 * For each 700 element with a $t and a 2nd indicator that isn't 2, the abcdegklmnopqrst4 subfields become the work/xc:relation and the $0 becomes the workID authority attribute.
	 * For each 700 element with a $t and a 2nd indicator that is 2, a separate work based on the $8 subfield (the linking tag) is created with a creator containing the abcdeg4 subfields and a title containing the klmnoprst subfields.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	@SuppressWarnings("unchecked")
	protected AggregateXCRecord process700(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Get the elements with the requested field
		List<Element> elements = transformMe.getDataFields("700");

		// Process each 700 field
		for(Element element : elements)
		{
			// Get the $t, $8, and 2nd indicator since they determine how we should process the field
			String ind2 = MarcXmlRecord.getIndicatorOfField(element, "2");
			List<String> linkingFields = MarcXmlRecord.getSubfieldOfField(element, '8');
			List<String> tFields = MarcXmlRecord.getSubfieldOfField(element, 't');

			// If there's no $t, create an expression level xc:contributor
			if(tFields.size() <= 0)
			{
				// Get the $0 fields; they contain the authority information
				List<String> authorityFields = MarcXmlRecord.getSubfieldOfField(element, '0');

				// Get the authority attribute for the xc:contributor field we're going to add
				Attribute authorityAttribute = null;
				for(String authorityField : authorityFields)
				{
					authorityAttribute = getAttributeForAuthority("700", authorityField, false);
					if(authorityAttribute != null)
						break;
				}

				// A StringBuilder to concat the values of all the subfields of the Element
				StringBuilder builder = new StringBuilder();

				// The subfields which should be processed
				String targetSubfields = "abcdegq";

				// The name of the element to add
				String elementName = Constants.ELEMENT_CONTRIBUTOR;
				Namespace elementNamespace = AggregateXCRecord.XC_NAMESPACE;

				// Get the $4 element, which we'll use to add "role" elements
				List<String> roleSubfields = MarcXmlRecord.getSubfieldOfField(element, '4');

				// Get the subfields of the current element
				List<Element> subfields = element.getChildren("subfield", marcNamespace);

				// Iterate over the subfields, and append each one to the StringBuilder if it
				// is in the list of target subfields
				for(Element subfield : subfields)
				{
					// Get the subfield's code
					String subfieldCode = subfield.getAttribute("code").getValue();

					if(targetSubfields.contains(subfieldCode))
						builder.append(subfield.getText() + " ");
				}

				// If any target fields were found
				if(builder.length() > 0)
				{
					String value = builder.substring(0, builder.length()-1); // The value is everything except the last space

					if(roleSubfields.size() <= 0)
					{
						// Setup the attribute list for the XC record field
						ArrayList<Attribute> attributes = new ArrayList<Attribute>();
						if(authorityAttribute != null)
							attributes.add(authorityAttribute);

						if(LOG.isDebugEnabled())
							LOG.debug("Adding a " + FrbrLevel.EXPRESSION + " level " + elementName + (authorityAttribute == null ? "" : " with an agentID of \"" + authorityAttribute.getValue() + "\"") + " based on the concatination of the 700's subfields' value, which is " + value);

						// Add the element to the XC record
						getXCRecordService().addElement(transformInto, elementName, value.trim(), elementNamespace, attributes, FrbrLevel.EXPRESSION);
					}
					else
					{
						elementNamespace = AggregateXCRecord.RDAROLE_NAMESPACE;

						boolean addedRole = false;

						for(String roleSubfield : roleSubfields)
						{
							if(roleSubfield.length() < 3 || !roles.containsKey(roleSubfield.substring(0, 3)))
								continue;

							elementName = roles.get(roleSubfield.substring(0, 3));

							FrbrLevel level = FrbrLevel.WORK;
							if(elementName.equals("director") || elementName.equals("editor") || elementName.equals("ilustrator") || elementName.equals("performer") || elementName.equals("producer") || elementName.equals("translator"))
								level = FrbrLevel.EXPRESSION;

							// Setup the attribute list for the XC record field
							ArrayList<Attribute> attributes = new ArrayList<Attribute>();
							if(authorityAttribute != null)
								attributes.add((Attribute)authorityAttribute.clone());

							if(LOG.isDebugEnabled())
								LOG.debug("Adding a " + level + " level " + elementName + (authorityAttribute == null ? "" : " with an agentID of \"" + authorityAttribute.getValue() + "\"") + " based on the concatination of the 700's subfields' value, which is " + value);

							// Add the element to the XC record
							getXCRecordService().addElement(transformInto, elementName, value.trim(), elementNamespace, attributes, level);

							addedRole = true;
						}

						if(!addedRole)
						{
							elementName = "contributor";
							elementNamespace = AggregateXCRecord.XC_NAMESPACE;

							// Setup the attribute list for the XC record field
							ArrayList<Attribute> attributes = new ArrayList<Attribute>();
							if(authorityAttribute != null)
								attributes.add((Attribute)authorityAttribute.clone());

							if(LOG.isDebugEnabled())
								LOG.debug("Adding a " + FrbrLevel.EXPRESSION + " level " + elementName + (authorityAttribute == null ? "" : " with an agentID of \"" + authorityAttribute.getValue() + "\"") + " based on the concatination of the 700's subfields' value, which is " + value);

							// Add the element to the XC record
							getXCRecordService().addElement(transformInto, elementName, value.trim(), elementNamespace, attributes, FrbrLevel.EXPRESSION);
						}
					}
				}
			}
			else
			{ 
				// See Word doc
				if(ind2.equals("2"))
				{
					// t's with 2nd ind as 2
					
					// The subfields to map to the title and creator respectively
					String titleOfSubfields = "kmnoprst";
									
					// StringBuilders to concat the values of all the subfields of the Element for the XCRecord's creator and title
					StringBuilder titleBuilder = new StringBuilder();
					
					
					// Get the subfields of the current element
					List<Element> subfields = element.getChildren("subfield", marcNamespace);

					// If the field had a linking tag, then that linking tag is used to identify the
					// work we're creating.  Otherwise we'll use an artificial linking tag to make
					// sure everything gets put where it belongs.
					String linkingTag = (linkingFields.size() > 0 ? linkingFields.get(0) : "700" + artificialLinkingId);

					// Iterate over the subfields, and append each one to the StringBuilder if it
					// is in the list of target subfields
					for(Element subfield : subfields){
						// Get the subfield's code
						String subfieldCode = subfield.getAttribute("code").getValue();

						// If the current subfield belongs in the title, append it
						if(titleOfSubfields.contains(subfieldCode))
							titleBuilder.append(subfield.getText() + " ");
						
						if(subfieldCode.equals("0"))
							transformInto = addElementForAuthority(transformMe, transformInto, "700", subfield.getText(), linkingTag);
					}

					// If any title fields were found
					if(titleBuilder.length() > 0)
					{
						String value = titleBuilder.substring(0, titleBuilder.length()-1); // The value is everything except the last space

						if(LOG.isDebugEnabled())
							LOG.debug("Adding a " + FrbrLevel.WORK + " level title based on the concatination of the 700's subfields' value, which is " + value);

						// Add an xc:title based on the 700 subfields' values
						// Add to the non-default work element for this 700 field
						
						//transformInto.addElementBasedOnLinkingField(Constants.ELEMENT_TITLE_OF_THE_WORK, value, XCRecord.XC_NAMESPACE, titleAttributes, linkingTag);
						
						Hashtable<String , Element>  workSubElements = new Hashtable<String, Element>();
						Element titleOfWorkElement = new Element(Constants.ELEMENT_TITLE_OF_WORK, AggregateXCRecord.RDVOCAB_NAMESPACE);
						titleOfWorkElement.setText(titleBuilder.toString());
						
						workSubElements.put(Constants.ELEMENT_TITLE_OF_WORK, titleOfWorkElement);
						if(linkingTag!=null && linkedCreatorFields.get(linkingTag) != null)
							workSubElements.put(Constants.ELEMENT_CREATOR, linkedCreatorFields.get(linkingTag));

						Hashtable<String , Element>  expressionSubElements = new Hashtable<String, Element>();
						Element titleOfExpressionElement = new Element(Constants.ELEMENT_TITLE_OF_EXPRESSION, AggregateXCRecord.RDVOCAB_NAMESPACE);
						titleOfExpressionElement.setText(titleBuilder.toString());
						expressionSubElements.put(Constants.ELEMENT_TITLE_OF_EXPRESSION, titleOfExpressionElement);
						
						getXCRecordService().addLinkedWorkAndExpression(transformInto, workSubElements, expressionSubElements);
						
					}
				
					// Increment the artificial linking ID so the next 700 gets mapped to a separate work element
					artificialLinkingId++;
				}
				else
				{
					// Get the $0 fields; they contain the authority information
					List<String> authorityFields = MarcXmlRecord.getSubfieldOfField(element, '0');

					// Get the authority attribute for the xc:contributor field we're going to add
					Attribute authorityAttribute = null;
					for(String authorityField : authorityFields)
					{
						authorityAttribute = getAttributeForAuthority("700", authorityField, true);
						if(authorityAttribute != null)
							break;
					}

					// A StringBuilder to concat the values of all the subfields of the Element
					StringBuilder builder = new StringBuilder();

					// The subfields which should be processed
					String targetSubfields = "abcdegklmnopqrst4";

					// The name of the element to add
					String elementName = "relation";

					// Get the subfields of the current element
					List<Element> subfields = element.getChildren("subfield", marcNamespace);

					// Iterate over the subfields, and append each one to the StringBuilder if it
					// is in the list of target subfields
					for(Element subfield : subfields)
					{
						// Get the subfield's code
						String subfieldCode = subfield.getAttribute("code").getValue();

						if(targetSubfields.contains(subfieldCode))
							builder.append(subfield.getText() + " ");
					}

					// If any target fields were found
					if(builder.length() > 0)
					{
						String value = builder.substring(0, builder.length()-1); // The value is everything except the last space

						if(LOG.isDebugEnabled())
							LOG.debug("Adding a " + FrbrLevel.WORK + " level " + elementName + (authorityAttribute == null ? "" : " with a " + authorityAttribute.getName() + " of \"" + authorityAttribute.getValue() + "\"") + " based on the concatination of the 700's subfields' value, which is " + value);

						// Setup the attribute list
						ArrayList<Attribute> attributes = new ArrayList<Attribute>();
						if(authorityAttribute != null)
							attributes.add((Attribute)authorityAttribute);

						// Add the element to the XC record
						getXCRecordService().addElement(transformInto, elementName, value.trim(), AggregateXCRecord.XC_NAMESPACE, attributes, FrbrLevel.WORK);
						
					}
				}
			}
		}

		
		// Return the result
		return transformInto;
	}

	/**
	 * Processes the 710 field from the MarcXmlRecord we're transforming.
	 * For each 710 element with a $t subfield, the $0 subfields become the rdvocab:identifierForTheWork
	 * field at the work FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	@SuppressWarnings("unchecked")
	protected AggregateXCRecord process710(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Get the elements with the requested field
		List<Element> elements = transformMe.getDataFields("710");

		// Process each 710 field
		for(Element element : elements)
		{
			// Get the $t, $8, and 2nd indicator since they determine how we should process the field
			String ind2 = MarcXmlRecord.getIndicatorOfField(element, "2");
			List<String> linkingFields = MarcXmlRecord.getSubfieldOfField(element, '8');
			List<String> tFields = MarcXmlRecord.getSubfieldOfField(element, 't');

			// If there's no $t, create an expression level xc:contributor
			if(tFields.size() <= 0)
			{
				// Get the $0 fields; they contain the authority information
				List<String> authorityFields = MarcXmlRecord.getSubfieldOfField(element, '0');

				// Get the authority attribute for the xc:contributor field we're going to add
				Attribute authorityAttribute = null;
				for(String authorityField : authorityFields)
				{
					authorityAttribute = getAttributeForAuthority("710", authorityField, false);
					if(authorityAttribute != null)
						break;
				}

				// A StringBuilder to concat the values of all the subfields of the Element
				StringBuilder builder = new StringBuilder();

				// The subfields which should be processed
				String targetSubfields = "abcdegq";

				// The name of the element to add
				String elementName = "contributor";
				Namespace elementNamespace = AggregateXCRecord.XC_NAMESPACE;

				// Get the $4 element, which we'll use to add "role" elements
				List<String> roleSubfields = MarcXmlRecord.getSubfieldOfField(element, '4');

				// Get the subfields of the current element
				List<Element> subfields = element.getChildren("subfield", marcNamespace);

				// Iterate over the subfields, and append each one to the StringBuilder if it
				// is in the list of target subfields
				for(Element subfield : subfields)
				{
					// Get the subfield's code
					String subfieldCode = subfield.getAttribute("code").getValue();

					if(targetSubfields.contains(subfieldCode))
						builder.append(subfield.getText() + " ");
				}

				// If any target fields were found
				if(builder.length() > 0)
				{
					String value = builder.substring(0, builder.length()-1); // The value is everything except the last space

					if(roleSubfields.size() <= 0)
					{
						// Setup the attribute list for the XC record field
						ArrayList<Attribute> attributes = new ArrayList<Attribute>();
						if(authorityAttribute != null)
							attributes.add(authorityAttribute);

						if(LOG.isDebugEnabled())
							LOG.debug("Adding a " + FrbrLevel.EXPRESSION + " level " + elementName + (authorityAttribute == null ? "" : " with an agentID of \"" + authorityAttribute.getValue() + "\"") + " based on the concatination of the 700's subfields' value, which is " + value);

						// Add the element to the XC record
						getXCRecordService().addElement(transformInto, elementName, value.trim(), elementNamespace, attributes, FrbrLevel.EXPRESSION);
					}
					else
					{
						elementNamespace = AggregateXCRecord.RDAROLE_NAMESPACE;

						boolean addedRole = false;

						for(String roleSubfield : roleSubfields)
						{
							if(roleSubfield.length() < 3 || !roles.containsKey(roleSubfield.substring(0, 3)))
								continue;

							elementName = roles.get(roleSubfield.substring(0, 3));

							FrbrLevel level = FrbrLevel.WORK;
							if(elementName.equals("director") || elementName.equals("editor") || elementName.equals("ilustrator") || elementName.equals("performer") || elementName.equals("producer") || elementName.equals("translator"))
								level = FrbrLevel.EXPRESSION;

							// Setup the attribute list for the XC record field
							ArrayList<Attribute> attributes = new ArrayList<Attribute>();
							if(authorityAttribute != null)
								attributes.add((Attribute)authorityAttribute.clone());

							if(LOG.isDebugEnabled())
								LOG.debug("Adding a " + level + " level " + elementName + (authorityAttribute == null ? "" : " with an agentID of \"" + authorityAttribute.getValue() + "\"") + " based on the concatination of the 700's subfields' value, which is " + value);

							// Add the element to the XC record
							getXCRecordService().addElement(transformInto, elementName, value.trim(), elementNamespace, attributes, level);

							addedRole = true;
						}

						if(!addedRole)
						{
							elementName = "contributor";
							elementNamespace = AggregateXCRecord.XC_NAMESPACE;

							// Setup the attribute list for the XC record field
							ArrayList<Attribute> attributes = new ArrayList<Attribute>();
							if(authorityAttribute != null)
								attributes.add((Attribute)authorityAttribute.clone());

							if(LOG.isDebugEnabled())
								LOG.debug("Adding a " + FrbrLevel.EXPRESSION + " level " + elementName + (authorityAttribute == null ? "" : " with an agentID of \"" + authorityAttribute.getValue() + "\"") + " based on the concatination of the 700's subfields' value, which is " + value);

							// Add the element to the XC record
							getXCRecordService().addElement(transformInto, elementName, value.trim(), elementNamespace, attributes, FrbrLevel.EXPRESSION);
						}
					}
				}
			}
			else
			{
				if(ind2.equals("2"))
				{
					// The subfields to map to the title and creator respectively
					String titleSubfields = "kmnoprst";

					// StringBuilders to concat the values of all the subfields of the Element for the XCRecord's creator and title
					StringBuilder titleBuilder = new StringBuilder();

					// Get the subfields of the current element
					List<Element> subfields = element.getChildren("subfield", marcNamespace);

					// If the field had a linking tag, then that linking tag is used to identify the
					// work we're creating.  Otherwise we'll use an artificial linking tag to make
					// sure everything gets put where it belongs.
					String linkingTag = (linkingFields.size() > 0 ? linkingFields.get(0) : "710" + artificialLinkingId);

					// Iterate over the subfields, and append each one to the StringBuilder if it
					// is in the list of target subfields
					for(Element subfield : subfields)
					{
						// Get the subfield's code
						String subfieldCode = subfield.getAttribute("code").getValue();

						// If the current subfield belongs in the title, append it
						if(titleSubfields.contains(subfieldCode))
							titleBuilder.append(subfield.getText() + " ");
						
						if(subfieldCode.equals("0"))
							transformInto = addElementForAuthority(transformMe, transformInto, "710", subfield.getText(), linkingTag);
					}

					// If any title fields were found
					if(titleBuilder.length() > 0)
					{
						String value = titleBuilder.substring(0, titleBuilder.length()-1); // The value is everything except the last space

						if(LOG.isDebugEnabled())
							LOG.debug("Adding a " + FrbrLevel.WORK + " level title based on the concatination of the 700's subfields' value, which is " + value);

						// Add an xc:title based on the 700 subfields' values
						// Add to the non-default work element for this 700 field
						
						//transformInto.addElementBasedOnLinkingField(Constants.ELEMENT_TITLE_OF_THE_WORK, value, XCRecord.XC_NAMESPACE, titleAttributes, linkingTag);
						
						Hashtable<String , Element>  workSubElements = new Hashtable<String, Element>();
						Element titleOfWorkElement = new Element(Constants.ELEMENT_TITLE_OF_WORK, AggregateXCRecord.RDVOCAB_NAMESPACE);
						titleOfWorkElement.setText(titleBuilder.toString());
						
						workSubElements.put(Constants.ELEMENT_TITLE_OF_WORK, titleOfWorkElement);
						if(linkingTag!=null && linkedCreatorFields.get(linkingTag) != null)
							workSubElements.put(Constants.ELEMENT_CREATOR, linkedCreatorFields.get(linkingTag));

						Hashtable<String , Element>  expressionSubElements = new Hashtable<String, Element>();
						Element titleOfExpressionElement = new Element(Constants.ELEMENT_TITLE_OF_EXPRESSION, AggregateXCRecord.RDVOCAB_NAMESPACE);
						titleOfExpressionElement.setText(titleBuilder.toString());
						expressionSubElements.put(Constants.ELEMENT_TITLE_OF_EXPRESSION, titleOfExpressionElement);
						
						getXCRecordService().addLinkedWorkAndExpression(transformInto, workSubElements, expressionSubElements);
						
					}
					
					// Increment the artificial linking ID so the next 710 gets mapped to a seperate work element
					artificialLinkingId++;
				}
				else
				{
					// Get the $0 fields; they contain the authority information
					List<String> authorityFields = MarcXmlRecord.getSubfieldOfField(element, '0');

					// Get the authority attribute for the xc:contributor field we're going to add
					Attribute authorityAttribute = null;
					for(String authorityField : authorityFields)
					{
						authorityAttribute = getAttributeForAuthority("710", authorityField, true);
						if(authorityAttribute != null)
							break;
					}

					// A StringBuilder to concat the values of all the subfields of the Element
					StringBuilder builder = new StringBuilder();

					// The subfields which should be processed
					String targetSubfields = "abcdegklmnopqrst4";

					// The name of the element to add
					String elementName = "relation";

					// Get the subfields of the current element
					List<Element> subfields = element.getChildren("subfield", marcNamespace);

					// Iterate over the subfields, and append each one to the StringBuilder if it
					// is in the list of target subfields
					for(Element subfield : subfields)
					{
						// Get the subfield's code
						String subfieldCode = subfield.getAttribute("code").getValue();

						if(targetSubfields.contains(subfieldCode))
							builder.append(subfield.getText() + " ");
					}

					// If any target fields were found
					if(builder.length() > 0)
					{
						String value = builder.substring(0, builder.length()-1); // The value is everything except the last space

						if(LOG.isDebugEnabled())
							LOG.debug("Adding a " + FrbrLevel.WORK + " level " + elementName + (authorityAttribute == null ? "" : " with a " + authorityAttribute.getName() + " of \"" + authorityAttribute.getValue() + "\"") + " based on the concatination of the 710's subfields' value, which is " + value);

						// Setup the attribute list
						ArrayList<Attribute> attributes = new ArrayList<Attribute>();
						if(authorityAttribute != null)
							attributes.add((Attribute)authorityAttribute);

						// Add the element to the XC record
						getXCRecordService().addElement(transformInto, elementName, value.trim(), AggregateXCRecord.XC_NAMESPACE, attributes, FrbrLevel.WORK);
					}
				}
			}
		}

		// Return the result
		return transformInto;
	}

	/**
	 * Processes the 711 field from the MarcXmlRecord we're transforming.
	 * Each 711 element becomes its own work element within the XC record.
	 * The abcdeg4 subfields become the creator and the klmnoprst subfields become the title.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	@SuppressWarnings("unchecked")
	protected AggregateXCRecord process711(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Get the elements with the requested field
		List<Element> elements = transformMe.getDataFields("711");

		// Process each 711 field
		for(Element element : elements)
		{
			// Get the $t, $8, and 2nd indicator since they determine how we should process the field
			String ind2 = MarcXmlRecord.getIndicatorOfField(element, "2");
			List<String> linkingFields = MarcXmlRecord.getSubfieldOfField(element, '8');
			List<String> tFields = MarcXmlRecord.getSubfieldOfField(element, 't');

			// If there's no $t, create an expression level xc:contributor
			if(tFields.size() <= 0)
			{
				// Get the $0 fields; they contain the authority information
				List<String> authorityFields = MarcXmlRecord.getSubfieldOfField(element, '0');

				// Get the authority attribute for the xc:contributor field we're going to add
				Attribute authorityAttribute = null;
				for(String authorityField : authorityFields)
				{
					authorityAttribute = getAttributeForAuthority("711", authorityField, false);
					if(authorityAttribute != null)
						break;
				}

				// A StringBuilder to concat the values of all the subfields of the Element
				StringBuilder builder = new StringBuilder();

				// The subfields which should be processed
				String targetSubfields = "abcdegnq";

				// The name of the element to add
				String elementName = "contributor";
				Namespace elementNamespace = AggregateXCRecord.XC_NAMESPACE;

				// Get the $4 element, which we'll use to add "role" elements
				List<String> roleSubfields = MarcXmlRecord.getSubfieldOfField(element, '4');

				// Get the subfields of the current element
				List<Element> subfields = element.getChildren("subfield", marcNamespace);

				// Iterate over the subfields, and append each one to the StringBuilder if it
				// is in the list of target subfields
				for(Element subfield : subfields)
				{
					// Get the subfield's code
					String subfieldCode = subfield.getAttribute("code").getValue();

					if(targetSubfields.contains(subfieldCode))
						builder.append(subfield.getText() + " ");
				}

				// If any target fields were found
				if(builder.length() > 0)
				{
					String value = builder.substring(0, builder.length()-1); // The value is everything except the last space

					if(roleSubfields.size() <= 0)
					{
						// Setup the attribute list for the XC record field
						ArrayList<Attribute> attributes = new ArrayList<Attribute>();
						if(authorityAttribute != null)
							attributes.add(authorityAttribute);

						if(LOG.isDebugEnabled())
							LOG.debug("Adding a " + FrbrLevel.EXPRESSION + " level " + elementName + (authorityAttribute == null ? "" : " with an agentID of \"" + authorityAttribute.getValue() + "\"") + " based on the concatination of the 700's subfields' value, which is " + value);

						// Add the element to the XC record
						getXCRecordService().addElement(transformInto, elementName, value.trim(), elementNamespace, attributes, FrbrLevel.EXPRESSION);
					}
					else
					{
						elementNamespace = AggregateXCRecord.RDAROLE_NAMESPACE;

						boolean addedRole = false;

						for(String roleSubfield : roleSubfields)
						{
							if(roleSubfield.length() < 3 || !roles.containsKey(roleSubfield.substring(0, 3)))
								continue;

							elementName = roles.get(roleSubfield.substring(0, 3));

							FrbrLevel level = FrbrLevel.WORK;
							if(elementName.equals("director") || elementName.equals("editor") || elementName.equals("ilustrator") || elementName.equals("performer") || elementName.equals("producer") || elementName.equals("translator"))
								level = FrbrLevel.EXPRESSION;

							// Setup the attribute list for the XC record field
							ArrayList<Attribute> attributes = new ArrayList<Attribute>();
							if(authorityAttribute != null)
								attributes.add((Attribute)authorityAttribute.clone());

							if(LOG.isDebugEnabled())
								LOG.debug("Adding a " + level + " level " + elementName + (authorityAttribute == null ? "" : " with an agentID of \"" + authorityAttribute.getValue() + "\"") + " based on the concatination of the 700's subfields' value, which is " + value);

							// Add the element to the XC record
							getXCRecordService().addElement(transformInto, elementName, value.trim(), elementNamespace, attributes, level);

							addedRole = true;
						}

						if(!addedRole)
						{
							elementName = "contributor";
							elementNamespace = AggregateXCRecord.XC_NAMESPACE;

							// Setup the attribute list for the XC record field
							ArrayList<Attribute> attributes = new ArrayList<Attribute>();
							if(authorityAttribute != null)
								attributes.add((Attribute)authorityAttribute.clone());

							if(LOG.isDebugEnabled())
								LOG.debug("Adding a " + FrbrLevel.EXPRESSION + " level " + elementName + (authorityAttribute == null ? "" : " with an agentID of \"" + authorityAttribute.getValue() + "\"") + " based on the concatination of the 700's subfields' value, which is " + value);

							// Add the element to the XC record
							getXCRecordService().addElement(transformInto, elementName, value.trim(), elementNamespace, attributes, FrbrLevel.EXPRESSION);
						}
					}
				}
			}
			else
			{
				if(ind2.equals("2"))
				{
					// The subfields to map to the title and creator respectively
					String titleSubfields = "fkpst";

					// StringBuilders to concat the values of all the subfields of the Element for the XCRecord's creator and title
					StringBuilder titleBuilder = new StringBuilder();

					// Get the subfields of the current element
					List<Element> subfields = element.getChildren("subfield", marcNamespace);

					// If the field had a linking tag, then that linking tag is used to identify the
					// work we're creating.  Otherwise we'll use an artificial linking tag to make
					// sure everything gets put where it belongs.
					String linkingTag = (linkingFields.size() > 0 ? linkingFields.get(0) : "711" + artificialLinkingId);

					// Iterate over the subfields, and append each one to the StringBuilder if it
					// is in the list of target subfields
					for(Element subfield : subfields)
					{
						// Get the subfield's code
						String subfieldCode = subfield.getAttribute("code").getValue();

						// If the current subfield belongs in the title, append it
						if(titleSubfields.contains(subfieldCode))
							titleBuilder.append(subfield.getText() + " ");
						
						if(subfieldCode.equals("0"))
							transformInto = addElementForAuthority(transformMe, transformInto, "711", subfield.getText(), linkingTag);
					}

					// If any title fields were found
					if(titleBuilder.length() > 0)
					{
						String value = titleBuilder.substring(0, titleBuilder.length()-1); // The value is everything except the last space

						if(LOG.isDebugEnabled())
							LOG.debug("Adding a " + FrbrLevel.WORK + " level title based on the concatination of the 700's subfields' value, which is " + value);

						// Add an xc:title based on the 700 subfields' values
						// Add to the non-default work element for this 700 field
						
						//transformInto.addElementBasedOnLinkingField(Constants.ELEMENT_TITLE_OF_THE_WORK, value, XCRecord.XC_NAMESPACE, titleAttributes, linkingTag);
						
						Hashtable<String , Element>  workSubElements = new Hashtable<String, Element>();
						Element titleOfWorkElement = new Element(Constants.ELEMENT_TITLE_OF_WORK, AggregateXCRecord.RDVOCAB_NAMESPACE);
						titleOfWorkElement.setText(titleBuilder.toString());
						
						workSubElements.put(Constants.ELEMENT_TITLE_OF_WORK, titleOfWorkElement);
						if(linkingTag!=null && linkedCreatorFields.get(linkingTag) != null)
							workSubElements.put(Constants.ELEMENT_CREATOR, linkedCreatorFields.get(linkingTag));

						Hashtable<String , Element>  expressionSubElements = new Hashtable<String, Element>();
						Element titleOfExpressionElement = new Element(Constants.ELEMENT_TITLE_OF_EXPRESSION, AggregateXCRecord.RDVOCAB_NAMESPACE);
						titleOfExpressionElement.setText(titleBuilder.toString());
						expressionSubElements.put(Constants.ELEMENT_TITLE_OF_EXPRESSION, titleOfExpressionElement);
						
						getXCRecordService().addLinkedWorkAndExpression(transformInto, workSubElements, expressionSubElements);
						
					}

					// Increment the artificial linking ID so the next 711 gets mapped to a seperate work element
					artificialLinkingId++;
				}
				else
				{
					// Get the $0 fields; they contain the authority information
					List<String> authorityFields = MarcXmlRecord.getSubfieldOfField(element, '0');

					// Get the authority attribute for the xc:contributor field we're going to add
					Attribute authorityAttribute = null;
					for(String authorityField : authorityFields)
					{
						authorityAttribute = getAttributeForAuthority("711", authorityField, true);
						if(authorityAttribute != null)
							break;
					}

					// A StringBuilder to concat the values of all the subfields of the Element
					StringBuilder builder = new StringBuilder();

					// The subfields which should be processed
					String targetSubfields = "abcdegklmnopqrst4";

					// The name of the element to add
					String elementName = "relation";

					// Get the subfields of the current element
					List<Element> subfields = element.getChildren("subfield", marcNamespace);

					// Iterate over the subfields, and append each one to the StringBuilder if it
					// is in the list of target subfields
					for(Element subfield : subfields)
					{
						// Get the subfield's code
						String subfieldCode = subfield.getAttribute("code").getValue();

						if(targetSubfields.contains(subfieldCode))
							builder.append(subfield.getText() + " ");
					}

					// If any target fields were found
					if(builder.length() > 0)
					{
						String value = builder.substring(0, builder.length()-1); // The value is everything except the last space

						if(LOG.isDebugEnabled())
							LOG.debug("Adding a " + FrbrLevel.WORK + " level " + elementName + (authorityAttribute == null ? "" : " with a " + authorityAttribute.getName() + " of \"" + authorityAttribute.getValue() + "\"") + " based on the concatination of the 711's subfields' value, which is " + value);

						// Setup the attribute list
						ArrayList<Attribute> attributes = new ArrayList<Attribute>();
						if(authorityAttribute != null)
							attributes.add((Attribute)authorityAttribute);

						// Add the element to the XC record
						getXCRecordService().addElement(transformInto, elementName, value.trim(), AggregateXCRecord.XC_NAMESPACE, attributes, FrbrLevel.WORK);
					}
				}
			}
		}

		// Return the result
		return transformInto;
	}

	/**
	 * Processes the 720 field from the MarcXmlRecord we're transforming.
	 * The ae4 subfields become the dc:contributor field at the expression FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process720(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dc:contributor based on the 720 ae4 values
		return processFieldBasic(transformMe, transformInto, "720", "ae4", "contributor", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.EXPRESSION);
	}

	/**
	 * Processes the 730 field from the MarcXmlRecord we're transforming.
	 * Each 730 element becomes its own work element within the XC record.
	 * The abcdeg4 subfields become the creator and the klmnoprst subfields become the title.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	@SuppressWarnings("unchecked")
	protected AggregateXCRecord process730(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Get the elements with the requested field
		List<Element> elements = transformMe.getDataFields("730");
		
		// Process each 730 field
		for(Element element : elements)
		{
			// Get the $t, $8, and 2nd indicator since they determine how we should process the field
			String ind2 = MarcXmlRecord.getIndicatorOfField(element, "2");

			List<String> linkingFields = MarcXmlRecord.getSubfieldOfField(element, '8');
			
			if(ind2.equals("2"))
			{
				// The subfields to map to the title and creator respectively
				String titleOfWorkSubfields = "adgkmnoprst";
				String titleOfExpressionSubfields = "adgklmnoprst";
				
				// StringBuilders to concat the values of all the subfields of the Element for the XCRecord's creator and title
				StringBuilder titleOfWorkBuilder = new StringBuilder();
				StringBuilder titleOfExpressionBuilder = new StringBuilder();

				// Get the subfields of the current element
				List<Element> subfields = element.getChildren("subfield", marcNamespace);

				// Get the Linking Tag
				String linkingTag = (linkingFields.size() > 0 ? linkingFields.get(0) : "711" + artificialLinkingId);
				
				// Iterate over the subfields, and append each one to the StringBuilder if it
				// is in the list of target subfields
				for(Element subfield : subfields)
				{
					// Get the subfield's code
					String subfieldCode = subfield.getAttribute("code").getValue();

					// If the current subfield belongs in either the creator or the title, append it
					if(titleOfWorkSubfields.contains(subfieldCode))
						titleOfWorkBuilder.append(subfield.getText() + " ");

					if(titleOfExpressionSubfields.contains(subfieldCode))
						titleOfExpressionBuilder.append(subfield.getText() + " ");
					
					if(subfieldCode.equals("0"))
						transformInto = addElementForAuthority(transformMe, transformInto, "730", subfield.getText(), "730" + artificialLinkingId);
				}

				// If any title fields were found
				if(titleOfWorkBuilder.length() > 0 || titleOfExpressionBuilder.length() > 0)
				{
					String workTitleValue = titleOfWorkBuilder.substring(0, titleOfWorkBuilder.length()-1); // The value is everything except the last space
					String expressionTitleValue = titleOfExpressionBuilder.substring(0, titleOfExpressionBuilder.length()-1); // The value is everything except the last space

					if(LOG.isDebugEnabled())
						LOG.debug("Adding a " + FrbrLevel.WORK + " level title based on the concatination of the 730's subfields' value, which is " + workTitleValue);

				
					Hashtable<String , Element>  workSubElements = new Hashtable<String, Element>();
					
					if(titleOfWorkBuilder.length() > 0){

						Element titleOfWorkElement = new Element(Constants.ELEMENT_TITLE_OF_WORK, AggregateXCRecord.RDVOCAB_NAMESPACE);
						titleOfWorkElement.setText(workTitleValue.toString());
						workSubElements.put(Constants.ELEMENT_TITLE_OF_WORK, titleOfWorkElement);
						if(linkingTag!=null && linkedCreatorFields.get(linkingTag) != null)
							workSubElements.put(Constants.ELEMENT_CREATOR, linkedCreatorFields.get(linkingTag));
					
					}

					Hashtable<String , Element>  expressionSubElements = new Hashtable<String, Element>();
					
					if(titleOfExpressionBuilder.length() > 0){
						
						Element titleOfExpressionElement = new Element(Constants.ELEMENT_TITLE_OF_EXPRESSION, AggregateXCRecord.RDVOCAB_NAMESPACE);
						titleOfExpressionElement.setText(expressionTitleValue.toString());
						expressionSubElements.put(Constants.ELEMENT_TITLE_OF_EXPRESSION, titleOfExpressionElement);
					}
					
					getXCRecordService().addLinkedWorkAndExpression(transformInto, workSubElements, expressionSubElements);
					
				}
				// Increment the artificial linking ID so the next 730 gets mapped to a seperate work element
				artificialLinkingId++;
			}
			else
			{
				// Get the $0 fields; they contain the authority information
				List<String> authorityFields = MarcXmlRecord.getSubfieldOfField(element, '0');

				// Get the authority attribute for the xc:contributor field we're going to add
				Attribute authorityAttribute = null;
				for(String authorityField : authorityFields)
				{
					authorityAttribute = getAttributeForAuthority("730", authorityField);
					if(authorityAttribute != null)
						break;
				}

				// Get the $0 fields; they contain the authority information
				List<String> issnFields = MarcXmlRecord.getSubfieldOfField(element, 'x');

				// Get the authority attribute for the xc:contributor field we're going to add
				Attribute issnAttribute = null;

				if(issnFields != null && issnFields.size() > 0)
					issnAttribute = new Attribute("ISSN", issnFields.get(0));


				// A StringBuilder to concat the values of all the subfields of the Element
				StringBuilder builder = new StringBuilder();

				// The subfields which should be processed
				String targetSubfields = "adgklmnoprst";

				// The name of the element to add
				String elementName = "relation";

				// Get the subfields of the current element
				List<Element> subfields = element.getChildren("subfield", marcNamespace);

				// Iterate over the subfields, and append each one to the StringBuilder if it
				// is in the list of target subfields
				for(Element subfield : subfields)
				{
					// Get the subfield's code
					String subfieldCode = subfield.getAttribute("code").getValue();

					if(targetSubfields.contains(subfieldCode))
						builder.append(subfield.getText() + " ");
				}

				// If any target fields were found
				if(builder.length() > 0)
				{
					String value = builder.substring(0, builder.length()-1); // The value is everything except the last space

					if(LOG.isDebugEnabled())
						LOG.debug("Adding a " + FrbrLevel.WORK + " level " + elementName + (authorityAttribute == null ? "" : " with a " + authorityAttribute.getName() + " of \"" + authorityAttribute.getValue() + "\"") + " based on the concatination of the 730's subfields' value, which is " + value);

					// Setup the attribute list
					ArrayList<Attribute> attributes = new ArrayList<Attribute>();
					if(authorityAttribute != null)
						attributes.add(authorityAttribute);
					if(issnAttribute != null)
						attributes.add(issnAttribute);

					// Add the element to the XC record
					getXCRecordService().addElement(transformInto, elementName, value.trim(), AggregateXCRecord.XC_NAMESPACE, attributes, FrbrLevel.WORK);
				}
			}
		}

		// Return the result
		return transformInto;
	}

	/**
	 * Processes the 740 field from the MarcXmlRecord we're transforming.
	 * The anpv subfields become the dcterms:alternative field at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process740(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dcterms:alternative based on the 740 anpv values
		return processFieldBasic(transformMe, transformInto, "740", "anpv", "alternative", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 752 field from the MarcXmlRecord we're transforming.
	 * The abcdfgh0 subfields become the dcterms:coverage field at the work FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process752(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dcterms:coverage based on the 752 abcdfgh0 values
		return processFieldBasicDashSubfields(transformMe, transformInto, "752", "abcdfgh0", "coverage", AggregateXCRecord.XC_NAMESPACE, null, FrbrLevel.WORK);
	}

	/**
	 * Processes the 760 field from the MarcXmlRecord we're transforming.
	 * The agit3 subfields become the dcterms:isPartOf field with a dcterms:ISSN equal to the 760's $x value at the expression FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process760(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dcterms:isPartOf based on the 760 agit3 values with a dcterms:ISSN based on the corrosponding 760 $x value
		return processFieldAttributeFromSubfield(transformMe, transformInto, "760", "agit3", "isPartOf", AggregateXCRecord.DCTERMS_NAMESPACE, new Attribute("ISSN", "null", AggregateXCRecord.DCTERMS_NAMESPACE), 'x', null, FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 765 field from the MarcXmlRecord we're transforming.
	 * The agit3 subfields become the dcterms:isVersionOf field with a dcterms:ISSN equal to the 765's $x value and a dcterms:ISBN equal to the 765's $z value at the expression FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process765(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create a HashMap mapping the $x subfield to a dcterms:ISSN attribute and the $z subfield to a dcterms:ISBN attribute
		HashMap<String, Attribute> subfieldToAttribute = new HashMap<String, Attribute>();
		subfieldToAttribute.put("x", new Attribute("ISSN", "", AggregateXCRecord.DCTERMS_NAMESPACE));
		subfieldToAttribute.put("z", new Attribute("ISBN", "", AggregateXCRecord.DCTERMS_NAMESPACE));

		// Create an dcterms:isVersionOf based on the 765 agit3 values with a dcterms:ISSN based on the corrosponding 765 $x value and a dcterms:ISBN based on the corrosponding 765 $z value
		return processFieldAttributeFromSubfield(transformMe, transformInto, "765", "agit3", "isVersionOf", AggregateXCRecord.DCTERMS_NAMESPACE, subfieldToAttribute, FrbrLevel.EXPRESSION);
	}

	/**
	 * Processes the 770 field from the MarcXmlRecord we're transforming.
	 * The agit subfields become the dc:relation field with a dcterms:ISSN equal to the 770's $x value and a dcterms:ISBN equal to the 770's $z value at the work FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process770(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create a HashMap mapping the $x subfield to a dcterms:ISSN attribute and the $z subfield to a dcterms:ISBN attribute
		HashMap<String, Attribute> subfieldToAttribute = new HashMap<String, Attribute>();
		subfieldToAttribute.put("x", new Attribute("ISSN", "", AggregateXCRecord.DCTERMS_NAMESPACE));
		subfieldToAttribute.put("z", new Attribute("ISBN", "", AggregateXCRecord.DCTERMS_NAMESPACE));

		// Create an dc:relation based on the 770 agit values with a dcterms:ISSN based on the corrosponding 770 $x value and a dcterms:ISBN based on the corrosponding 770 $z value
		return processFieldAttributeFromSubfield(transformMe, transformInto, "770", "agit", "relation", AggregateXCRecord.DCTERMS_NAMESPACE, subfieldToAttribute, FrbrLevel.WORK);
	}

	/**
	 * Processes the 772 field from the MarcXmlRecord we're transforming.
	 * The agit subfields become the dc:relation field with a dcterms:ISSN equal to the 772's $x value and a dcterms:ISBN equal to the 772's $z value at the work FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process772(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create a HashMap mapping the $x subfield to a dcterms:ISSN attribute and the $z subfield to a dcterms:ISBN attribute
		HashMap<String, Attribute> subfieldToAttribute = new HashMap<String, Attribute>();
		subfieldToAttribute.put("x", new Attribute("ISSN", "", AggregateXCRecord.DCTERMS_NAMESPACE));
		subfieldToAttribute.put("z", new Attribute("ISBN", "", AggregateXCRecord.DCTERMS_NAMESPACE));

		// Create an dc:relation based on the 772 agit values with a dcterms:ISSN based on the corrosponding 772 $x value and a dcterms:ISBN based on the corrosponding 772 $z value
		return processFieldAttributeFromSubfield(transformMe, transformInto, "772", "agit", "relation", AggregateXCRecord.DCTERMS_NAMESPACE, subfieldToAttribute, FrbrLevel.WORK);
	}

	/**
	 * Processes the 773 field from the MarcXmlRecord we're transforming.
	 * The agitwxz3 subfields become the dcterms:isPartOf field with a dcterms:ISSN equal to the 773's $x value and a dcterms:ISBN equal to the 772's $z value at the expression FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process773(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create a HashMap mapping the $x subfield to a dcterms:ISSN attribute and the $z subfield to a dcterms:ISBN attribute
		HashMap<String, Attribute> subfieldToAttribute = new HashMap<String, Attribute>();
		subfieldToAttribute.put("x", new Attribute("ISSN", "", AggregateXCRecord.DCTERMS_NAMESPACE));
		subfieldToAttribute.put("z", new Attribute("ISBN", "", AggregateXCRecord.DCTERMS_NAMESPACE));

		// Create an dcterms:isPartOf based on the 773 agitwxz3 values with a dcterms:ISSN based on the corrosponding 773 $x value and a dcterms:ISBN based on the corrosponding 773 $z value
		return processFieldAttributeFromSubfield(transformMe, transformInto, "773", "agit3", "isPartOf", AggregateXCRecord.DCTERMS_NAMESPACE, subfieldToAttribute, FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 775 field from the MarcXmlRecord we're transforming.
	 * The agit subfields become the dc:relation field with a dcterms:ISSN equal to the 775's $x value and a dcterms:ISBN equal to the 775's $z value at the expression FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process775(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create a HashMap mapping the $x subfield to a dcterms:ISSN attribute and the $z subfield to a dcterms:ISBN attribute
		HashMap<String, Attribute> subfieldToAttribute = new HashMap<String, Attribute>();
		subfieldToAttribute.put("x", new Attribute("ISSN", "", AggregateXCRecord.DCTERMS_NAMESPACE));
		subfieldToAttribute.put("z", new Attribute("ISBN", "", AggregateXCRecord.DCTERMS_NAMESPACE));

		// Create an dc:relation based on the 775 agit values with a dcterms:ISSN based on the corrosponding 775 $x value and a dcterms:ISBN based on the corrosponding 775 $z value
		return processFieldAttributeFromSubfield(transformMe, transformInto, "775", "agit", "relation", AggregateXCRecord.DCTERMS_NAMESPACE, subfieldToAttribute, FrbrLevel.EXPRESSION);
	}

	/**
	 * Processes the 776 field from the MarcXmlRecord we're transforming.
	 * The agit subfields become the dcterms:isFormatOf field with a dcterms:ISSN equal to the 776's $x value and a dcterms:ISBN equal to the 776's $z value at the expression FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process776(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create a HashMap mapping the $x subfield to a dcterms:ISSN attribute and the $z subfield to a dcterms:ISBN attribute
		HashMap<String, Attribute> subfieldToAttribute = new HashMap<String, Attribute>();
		subfieldToAttribute.put("x", new Attribute("ISSN", "", AggregateXCRecord.DCTERMS_NAMESPACE));
		subfieldToAttribute.put("z", new Attribute("ISBN", "", AggregateXCRecord.DCTERMS_NAMESPACE));

		// Create an dcterms:isFormatOf based on the 776 agit values with a dcterms:ISSN based on the corrosponding 776 $x value and a dcterms:ISBN based on the corrosponding 776 $z value
		return processFieldAttributeFromSubfield(transformMe, transformInto, "776", "agit", "HasFormat", AggregateXCRecord.DCTERMS_NAMESPACE, subfieldToAttribute, FrbrLevel.EXPRESSION);
	}

	/**
	 * Processes the 777 field from the MarcXmlRecord we're transforming.
	 * The agit subfields become the dc:relation field with a dcterms:ISSN equal to the 777's $x value at the expression FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process777(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create a HashMap mapping the $x subfield to a dcterms:ISSN attribute
		HashMap<String, Attribute> subfieldToAttribute = new HashMap<String, Attribute>();
		subfieldToAttribute.put("x", new Attribute("ISSN", "", AggregateXCRecord.DCTERMS_NAMESPACE));

		// Create an dc:relation based on the 777 agit values with a dcterms:ISSN based on the corrosponding 777 $x value and a dcterms:ISBN based on the corrosponding 777 $z value
		return processFieldAttributeFromSubfield(transformMe, transformInto, "777", "agit", "relation", AggregateXCRecord.DCTERMS_NAMESPACE, subfieldToAttribute, FrbrLevel.EXPRESSION);
	}

	/**
	 * Processes the 780 field from the MarcXmlRecord we're transforming.
	 * The agitxz subfields become the dcterms:replaces field at the work FRBR level.
	 * The x subfield becomes the dcterms:ISSN attribute on the field.
	 * The z subfield becomes the dcterms:ISBN attribute on the field.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process780(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create the subfield to attribute map.
		// We'll map the $x subfield to a dcterms:ISSN attribute
		// and the $z subfield to the dcterms:ISBN attribute
		HashMap<String, Attribute> subfieldToAttribute = new HashMap<String, Attribute>();
		subfieldToAttribute.put("x", new Attribute("ISSN", "", AggregateXCRecord.DCTERMS_NAMESPACE));
		subfieldToAttribute.put("z", new Attribute("ISBN", "", AggregateXCRecord.DCTERMS_NAMESPACE));

		// Create an dcterms:replaces based on the 780 agitxz values
		return processFieldReqIndicatorAttFromField(transformMe, transformInto, "780", "agit", "replaces", AggregateXCRecord.DCTERMS_NAMESPACE, null, null, subfieldToAttribute, FrbrLevel.WORK);
	}

	/**
	 * Processes the 785 field from the MarcXmlRecord we're transforming.
	 * The agitxz subfields become the dcterms:isReplacedBy field at the work FRBR level.
	 * The x subfield becomes the dcterms:ISSN attribute on the field.
	 * The z subfield becomes the dcterms:ISBN attribute on the field.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process785(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create the subfield to attribute map.
		// We'll map the $x subfield to a dcterms:ISSN attribute
		// and the $z subfield to the dcterms:ISBN attribute
		HashMap<String, Attribute> subfieldToAttribute = new HashMap<String, Attribute>();
		subfieldToAttribute.put("x", new Attribute("ISSN", "null", AggregateXCRecord.DCTERMS_NAMESPACE));
		subfieldToAttribute.put("z", new Attribute("ISBN", "null", AggregateXCRecord.DCTERMS_NAMESPACE));

		// Create an dcterms:isReplacedBy based on the 785 agitxz values
		return processFieldReqIndicatorAttFromField(transformMe, transformInto, "785", "agit", "isReplacedBy", AggregateXCRecord.DCTERMS_NAMESPACE, null, null, subfieldToAttribute, FrbrLevel.WORK);
	}

	/**
	 * Processes the 786 field from the MarcXmlRecord we're transforming.
	 * The agit subfields become the dcterms:isVersionOf field with a dcterms:ISSN equal to the 786's $x value and a dcterms:ISBN equal to the 786's $z value at the expression FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process786(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create a HashMap mapping the $x subfield to a dcterms:ISSN attribute and the $z subfield to a dcterms:ISBN attribute
		HashMap<String, Attribute> subfieldToAttribute = new HashMap<String, Attribute>();
		subfieldToAttribute.put("x", new Attribute("ISSN", "", AggregateXCRecord.DCTERMS_NAMESPACE));
		subfieldToAttribute.put("z", new Attribute("ISBN", "", AggregateXCRecord.DCTERMS_NAMESPACE));

		// Create an dcterms:isVersionOf based on the 786 agit values with a dcterms:ISSN based on the corrosponding 786 $x value and a dcterms:ISBN based on the corrosponding 786 $z value
		return processFieldAttributeFromSubfield(transformMe, transformInto, "786", "agit", "isVersionOf", AggregateXCRecord.DCTERMS_NAMESPACE, subfieldToAttribute, FrbrLevel.EXPRESSION);
	}

	/**
	 * Processes the 787 field from the MarcXmlRecord we're transforming.
	 * The agit subfields become the dc:relation field with a dcterms:ISSN equal to the 787's $x value and a dcterms:ISBN equal to the 787's $z value at the expression FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process787(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create a HashMap mapping the $x subfield to a dcterms:ISSN attribute and the $z subfield to a dcterms:ISBN attribute
		HashMap<String, Attribute> subfieldToAttribute = new HashMap<String, Attribute>();
		subfieldToAttribute.put("x", new Attribute("ISSN", "", AggregateXCRecord.DCTERMS_NAMESPACE));
		subfieldToAttribute.put("z", new Attribute("ISBN", "", AggregateXCRecord.DCTERMS_NAMESPACE));

		// Create an dc:relation based on the 787 agit values with a dcterms:ISSN based on the corrosponding 787 $x value and a dcterms:ISBN based on the corrosponding 787 $z value
		return processFieldAttributeFromSubfield(transformMe, transformInto, "787", "agit", "relation", AggregateXCRecord.DCTERMS_NAMESPACE, subfieldToAttribute, FrbrLevel.EXPRESSION);
	}

	/**
	 * Processes the 800 field from the MarcXmlRecord we're transforming.
	 * The abcdegq4 subfields become the dcterms:isPartOf field at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process800(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dcterms:isReferencedBy based on the 800 abcdegq4klmnoprstv values with a dcterms:ISSN based on the corrosponding 510 $x value
		return processFieldWithAuthorityAttributeFromSubfield(transformMe, transformInto, "800", "abcdefgq4klmnoprstv", "isPartOf", AggregateXCRecord.DCTERMS_NAMESPACE, new Attribute("ISSN", "null", AggregateXCRecord.DCTERMS_NAMESPACE), 'x', FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 810 field from the MarcXmlRecord we're transforming.
	 * The abcdeg4 subfields become the dcterms:isPartOf field at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process810(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dcterms:isReferencedBy based on the 810 abcdeg4klmnoprstv values with a dcterms:ISSN based on the corrosponding 510 $x value
		return processFieldWithAuthorityAttributeFromSubfield(transformMe, transformInto, "810", "abcdefg4klmnoprstv", "isPartOf", AggregateXCRecord.DCTERMS_NAMESPACE, new Attribute("ISSN", "null", AggregateXCRecord.DCTERMS_NAMESPACE), 'x', FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 811 field from the MarcXmlRecord we're transforming.
	 * The acdefgjklnpqstv4 subfields become the dcterms:isPartOf field at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process811(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dcterms:isReferencedBy based on the 811 acdefgjklnpqstv4 values with a dcterms:ISSN based on the corrosponding 510 $x value
		return processFieldWithAuthorityAttributeFromSubfield(transformMe, transformInto, "811", "acdefgjklnpqstv4", "isPartOf", AggregateXCRecord.DCTERMS_NAMESPACE, new Attribute("ISSN", "null", AggregateXCRecord.DCTERMS_NAMESPACE), 'x', FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 830 field from the MarcXmlRecord we're transforming.
	 * The adfgklmnoprst subfields become the dcterms:isPartOf field at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process830(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dcterms:isReferencedBy based on the 830 adfgklmnoprstv values with a dcterms:ISSN based on the corrosponding 510 $x value
		return processFieldWithAuthorityAttributeFromSubfield(transformMe, transformInto, "830", "adfgklmnoprstv", "isPartOf", AggregateXCRecord.DCTERMS_NAMESPACE, new Attribute("ISSN", "null", AggregateXCRecord.DCTERMS_NAMESPACE), 'x', FrbrLevel.MANIFESTATION);
	}

	/*
	 * Commented for now. Will be implemented later.
	 * Processes the 843 field from the MarcXmlRecord we're transforming.
	 * The abcdefmn subfields become the xc:description field at the holdings FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	protected XCRecord holdingsProcess843(MarcXmlRecord transformMe, XCRecord transformInto)
	{
		// Create an xc:description based on the 843 abcdefmn values
		return processFieldBasic(transformMe, transformInto, "843", "abcdefmn", "description", XCRecord.XC_NAMESPACE, null, FrbrLevel.HOLDINGS);
	}
	*/

	
	/**
	 * Processes the 852 field from the MarcXmlRecord we're transforming.
	 * The abcefg subfields become the xc:location field at the holdings FRBR level.
	 * The hijklm subfields become the xc:callNumber field at the holdings FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	@SuppressWarnings("unchecked")
	protected AggregateXCRecord process852(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Get the elements with the requested tags in the MARC XML record
		List<Element> elements = transformMe.getDataFields("852");
		
		List<Element> relevantSiblings = new ArrayList<Element>();
		for (String df : new String[] {"866", "867", "868"}) {
			List<Element> sibs = transformMe.getDataFields(df);
			if (sibs != null && sibs.size() > 0)
				relevantSiblings.addAll(sibs);
		}

		// The subfields of the 852 datafield we're processing
		String locationTargetSubfields = "bc";
		String textualHoldingsTargetSubfields = "az";
		String callNumberTargetSubfields = "hijklm";

		// If there were matching elements, return the unmodified XC record
		if(elements.size() == 0)
			return transformInto;

		// Add each subfield to the specified level with the specified tag and attribute
		for(Element element : elements)
		{
			// A StringBuilder to concat the values of all the subfields of the Element
			ArrayList<String> locationValues = new ArrayList<String>();
			StringBuilder callNumberBuilder = new StringBuilder();
			StringBuilder subjectLCCBuilder = new StringBuilder();
			StringBuilder subjectDDCBuilder = new StringBuilder();

			// Get the subfields of the current element
			List<Element> subfields = element.getChildren("subfield", marcNamespace);

			// Iterate over the subfields, and append each one to the StringBuilder if it
			// is in the list of target subfields
			for(Element subfield : subfields)
			{
				// Get the subfield's code
				String subfieldCode = subfield.getAttribute("code").getValue();

				// Subject Mappings
				// If first indicator is 0, then map $h to work/dcterms:subject
				if(	MarcXmlRecord.getIndicatorOfField(element, "1").equals("0") && subfieldCode.equals("h")) 
				{
					boolean mapFlag = false;
					
					// Break if any characters (other than the first two) are letters
					for (int i = 0; i < subfield.getText().toCharArray().length ; i++) {
					
						// First character should be a letter
						if( i==0 && Character.isLetter(subfield.getText().charAt(i)))
							mapFlag = true;
						// Second character should be a letter
						else if(i==1 && Character.isLetter(subfield.getText().charAt(i)))
							mapFlag = true;
						// Other characters should be numerals
						else if(!Character.isLetter(subfield.getText().charAt(i)) )
							mapFlag = true;
						// Do not map
						else
						{
								mapFlag = false;
								break; // break the for loof
						}
							
					}// End of for loop
					
					if(mapFlag)
						subjectLCCBuilder = subjectLCCBuilder.append(subfield.getText());

				}
				// If first indicator is 1, then map $h to work/dcterms:subject
				else if(MarcXmlRecord.getIndicatorOfField(element, "1").equals("1") && subfieldCode.equals("h"))
				{
					boolean mapFlag = false;
					// Break if any characters (other than the first two) are numerals
					for (int i = 0; i < subfield.getText().toCharArray().length ; i++) {
						// First character should be a numeral
						if( i==0 && Character.isDigit(subfield.getText().charAt(i)))
							mapFlag = true;
						// Second character should be a numeral
						else if(i==1 && Character.isDigit(subfield.getText().charAt(i)))
							mapFlag = true;
						// Other characters should be a characters
						else if(!Character.isDigit(subfield.getText().charAt(i)) )
							mapFlag = true;
						// Do not map
						else
						{
								mapFlag = false;
								break; // break the for loof
						}
							
					}// End of for loop
					
					if(mapFlag)
						subjectDDCBuilder = subjectDDCBuilder.append(subfield.getText());				
					
				}
					
				if(locationTargetSubfields.contains(subfieldCode))
					locationValues.add(subfield.getText());

				if(callNumberTargetSubfields.contains(subfieldCode))
					callNumberBuilder.append(subfield.getText() + " ");
			}

			// A list of the values of the textual holdings elements, which are taken from
			// the 866, 867, and 868 datafields immediately following the current datafield.
			ArrayList<Element> textualHoldings = new ArrayList<Element>();

			
			for (Element sibling : relevantSiblings) {
				String siblingTag = sibling.getAttributeValue("tag");	
				StringBuilder textualHoldingsBuilder = new StringBuilder();

				// Get the subfields of the current element
				List<Element> siblingSubfields = sibling.getChildren("subfield", marcNamespace);

				// Iterate over the subfields, and append each one to the StringBuilder if it
				// is in the list of target subfields
				for(Element siblingSubfield : siblingSubfields)
				{
					// Get the subfield's code
					String siblingSubfieldCode = siblingSubfield.getAttribute("code").getValue();

					if(textualHoldingsTargetSubfields.contains(siblingSubfieldCode))
						textualHoldingsBuilder.append(siblingSubfield.getText() + " ");
				}

				if(textualHoldingsBuilder.length() > 0) {
					String textualHoldingsValue = textualHoldingsBuilder.substring(0, textualHoldingsBuilder.length()-1);
					if (siblingTag.equals("866")) {
						Attribute attribute = new Attribute("type","Basic Bibliographic Unit");
						textualHoldings.add(new Element("textualHoldings", AggregateXCRecord.XC_NAMESPACE).setText(textualHoldingsValue).setAttribute(attribute));
					} else if (siblingTag.equals("867")) {
						Attribute attribute = new Attribute("type","Supplementary material");
						textualHoldings.add(new Element("textualHoldings", AggregateXCRecord.XC_NAMESPACE).setText(textualHoldingsValue).setAttribute(attribute));
					} else if (siblingTag.equals("868")) {
						Attribute attribute = new Attribute("type","Indexes");
						textualHoldings.add(new Element("textualHoldings", AggregateXCRecord.XC_NAMESPACE).setText(textualHoldingsValue).setAttribute(attribute));
					}
				}				
			}

			// If any target fields were found
			List<Element> holdingsContent = new ArrayList<Element>();

			String callNumberValue = (callNumberBuilder.length() > 0 ? callNumberBuilder.substring(0, callNumberBuilder.length()-1) : ""); // The value is everything except the last space

			for(String location : locationValues) {
				holdingsContent.add(new Element("location", AggregateXCRecord.XC_NAMESPACE).setText(location));
			}
			if(callNumberValue.length() > 0) {
				holdingsContent.add(new Element("callNumber", AggregateXCRecord.XC_NAMESPACE).setText(callNumberValue));
			}
			for(Element textualHolding : textualHoldings) {
				holdingsContent.add(textualHolding);
			}

			getXCRecordService().addHoldingsElement(transformInto, holdingsContent);
				
			if(subjectLCCBuilder.length() > 0){
				ArrayList<Attribute> attributes = new ArrayList<Attribute>();
				attributes.add(new Attribute("type", "dcterms:LCC", AggregateXCRecord.XSI_NAMESPACE));
				getXCRecordService().addElement(transformInto, "subject", subjectLCCBuilder.toString(), AggregateXCRecord.DCTERMS_NAMESPACE, attributes, FrbrLevel.WORK);
			}
			
			if(subjectDDCBuilder.length() > 0){
				ArrayList<Attribute> attributes = new ArrayList<Attribute>();
				attributes.add(new Attribute("type", "dcterms:DDC", AggregateXCRecord.XSI_NAMESPACE));
				getXCRecordService().addElement(transformInto, "subject", subjectDDCBuilder.toString(), AggregateXCRecord.DCTERMS_NAMESPACE, attributes, FrbrLevel.WORK);
			}
			
		}

		// Return the result
		return transformInto;
	}

	/**
	 * Processes the 856 field from the MarcXmlRecord we're transforming.
	 * The abcdfhijklmnopqrstuvyz23 subfields become the a different field depending on the 2nd indicator.
	 * If 2nd indicator is 0, blank, or 8, map to manifestation level dcterms:identifier
	 * If 2nd indicator is 1, map to expression level dcterms:hasVersion
	 * If 2nd indicator is 2, map to expression level dc:relation
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	@SuppressWarnings("unchecked")
	protected AggregateXCRecord process856(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Get the elements with the requested field
		List<Element> elements = transformMe.getDataFields("856");

		// The subfields we're processing
		String targetSubfields = "abcdfhijklmnopqrstuvyz23";

		// If there were no matching elements, return the unmodified XC record
		if(elements.size() == 0)
			return transformInto;

		// Add each subfield to the specified level with the specified tag and attribute
		for(Element element : elements)
		{
			// Get the required indicator
			String ind2 = MarcXmlRecord.getIndicatorOfField(element, "2");

			// Setup the attribute list for the processed field
			ArrayList<Attribute> attributes = new ArrayList<Attribute>();

			// A StringBuilder to concat the values of all the subfields of the Element
			StringBuilder builder = new StringBuilder();

			// Get the subfields of the current element
			List<Element> subfields = element.getChildren("subfield", marcNamespace);

			// Iterate over the subfields, and append each one to the StringBuilder if it
			// is in the list of target subfields
			for(Element subfield : subfields)
			{
				// Get the subfield's code
				String subfieldCode = subfield.getAttribute("code").getValue();

				if(targetSubfields.contains(subfieldCode))
					builder.append(subfield.getText() + " ");
			}

			// If any target fields were found
			if(builder.length() > 0)
			{
				String value = builder.substring(0, builder.length()-1); // The value is everything except the last space

				// If 2nd indicator is 0, blank, or 8, map to manifestation level dcterms:identifier
				// If 2nd indicator is 1, map to expression level dcterms:hasVersion
				// If 2nd indicator is 2, map to expression level dc:relation
				if(ind2 == null || ind2.equals("0") || ind2.equals(" ") || ind2.equals("8"))
				{
					if(LOG.isDebugEnabled())
						LOG.debug("Adding a " + FrbrLevel.MANIFESTATION + " level identifier based on the concatination of the 856's subfields' value, which is " + value);

					// Create an dcterms:identifier based on the 856 abcdfhijklmnopqrstuvwxyz23 values
					getXCRecordService().addElement(transformInto, "identifier", value.trim(), AggregateXCRecord.DCTERMS_NAMESPACE, attributes, FrbrLevel.MANIFESTATION);
				}
				else if(ind2 != null && ind2.equals("1"))
				{
					if(LOG.isDebugEnabled())
						LOG.debug("Adding a " + FrbrLevel.EXPRESSION + " level alternative based on the concatination of the 856's subfields' value, which is " + value);

					// Create a dcterms:hasVersion based on the 856 abcdfhijklmnopqrstuvwxyz23 values
					getXCRecordService().addElement(transformInto, "hasVersion", value.trim(), AggregateXCRecord.DCTERMS_NAMESPACE, attributes, FrbrLevel.EXPRESSION);
				}
				else if(ind2 != null && ind2.equals("2"))
				{
					if(LOG.isDebugEnabled())
						LOG.debug("Adding a " + FrbrLevel.EXPRESSION + " level alternative based on the concatination of the 856's subfields' value, which is " + value);

					// Create a dc:relation based on the 856 abcdfhijklmnopqrstuvwxyz23 values
					getXCRecordService().addElement(transformInto, "relation", value.trim(), AggregateXCRecord.DCTERMS_NAMESPACE, attributes, FrbrLevel.EXPRESSION);
				}
			}
		}

		// Return the result
		return transformInto;
	}

	/**
	 * Processes the 866 field from the MarcXmlRecord we're transforming.
	 * The az subfields become the xc:textualHoldings field at the holdings FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process866(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Get the elements with the requested field
		List<Element> elements = transformMe.getDataFields("852");
		
		// Create textualHoldings only when there is no 852 field. If there are 852 fields, then textualHoldings would have been created while processing 852 field. See process852()
		if (elements == null || elements.size() == 0) {
			// Create an xc:description based on the 866 abcdefmn values
			return processFieldBasic(transformMe, transformInto, "866", "az", "textualHoldings", AggregateXCRecord.XC_NAMESPACE, new Attribute("type","Basic Bibliographic Unit"), FrbrLevel.HOLDINGS);
		}
		
		return transformInto;
	}

	/**
	 * Processes the 867 field from the MarcXmlRecord we're transforming.
	 * The az subfields become the xc:textualHoldings field at the holdings FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process867(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Get the elements with the requested field
		List<Element> elements = transformMe.getDataFields("852");
		
		// Create textualHoldings only when there is no 852 field. If there are 852 fields, then textualHoldings would have been created while processing 852 field. See process852()
		if (elements == null || elements.size() == 0) {
			// Create an xc:description based on the 867 abcdefmn values
			return processFieldBasic(transformMe, transformInto, "867", "az", "textualHoldings", AggregateXCRecord.XC_NAMESPACE, new Attribute("type","Supplementary material"), FrbrLevel.HOLDINGS);
		}
		
		return transformInto;
	}

	/**
	 * Processes the 868 field from the MarcXmlRecord we're transforming.
	 * The az subfields become the xc:textualHoldings field at the holdings FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process868(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Get the elements with the requested field
		List<Element> elements = transformMe.getDataFields("852");
		
		// Create textualHoldings only when there is no 852 field. If there are 852 fields, then textualHoldings would have been created while processing 852 field. See process852()
		if (elements == null || elements.size() == 0) {
			// Create an xc:description based on the 867 abcdefmn values
			return processFieldBasic(transformMe, transformInto, "868", "az", "textualHoldings", AggregateXCRecord.XC_NAMESPACE, new Attribute("type","Indexes"), FrbrLevel.HOLDINGS);
		}
		
		return transformInto;
	}
	
	
	/**
	 * Processes the 931 field from the MarcXmlRecord we're transforming.
	 * The $a subfields become dcterms:type fields at the expression FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process931(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dcterms:type based on the 931 $a values
		return processFieldBasic(transformMe, transformInto, "931", 'a', "type", AggregateXCRecord.DCTERMS_NAMESPACE, new Attribute("type", "dcterms:DCMIType", AggregateXCRecord.XSI_NAMESPACE), FrbrLevel.EXPRESSION);
	}

	/**
	 * Processes the 932 field from the MarcXmlRecord we're transforming.
	 * The $a subfields become xc:typeLeader06 fields at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process932(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an xc:typeLeader06 based on the 932 $a values
		return processFieldBasic(transformMe, transformInto, "932", 'a', "typeLeader06", AggregateXCRecord.XC_NAMESPACE, null, FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 933 field from the MarcXmlRecord we're transforming.
	 * The $a subfields become xc:type007 fields at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process933(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an xc:type007 based on the 933 $a values
		return processFieldBasic(transformMe, transformInto, "933", 'a', "type007", AggregateXCRecord.XC_NAMESPACE, null, FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 934 field from the MarcXmlRecord we're transforming.
	 * The $a subfields become xc:typeSMD fields at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process934(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an xc:typeSMD based on the 934 $a values
		return processFieldBasic(transformMe, transformInto, "934", 'a', "typeSMD", AggregateXCRecord.XC_NAMESPACE, null, FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 935 field from the MarcXmlRecord we're transforming.
	 * The $a subfields become rdvocab:modeOfIssuance fields at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process935(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an rdvocab:modeOfIssuance based on the 935 $a values
		return processFieldBasic(transformMe, transformInto, "935", 'a', "modeOfIssuance", AggregateXCRecord.RDVOCAB_NAMESPACE, null, FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 937 field from the MarcXmlRecord we're transforming.
	 * The $a subfields become rdvocab:natureOfContent fields at the work FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process937(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an rdvocab:natureOfContent based on the 937 $a values
		return processFieldBasic(transformMe, transformInto, "937", 'a', "natureOfContent", AggregateXCRecord.RDVOCAB_NAMESPACE, null, FrbrLevel.WORK);
	}

	/**
	 * Processes the 939 field from the MarcXmlRecord we're transforming.
	 * The $a subfields become dcterms:issued fields at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process939(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dcterms:issued based on the 939 $a values
		return processFieldBasic(transformMe, transformInto, "939", 'a', "issued", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 943 field from the MarcXmlRecord we're transforming.
	 * The $a subfields become dcterms:language fields at the expression FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process943(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dcterms:language based on the 943 $a values
		return processFieldBasic(transformMe, transformInto, "943", 'a', "language", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.EXPRESSION);
	}

	/**
	 * Processes the 945 field from the MarcXmlRecord we're transforming.
	 * The $a subfields become dcterms:audience fields at the work FRBR level.
	 * The $ab subfields become xc:callNumber fields at the Holding FRBR level.
	 * The $l subfields become xc:location fields at the Holding FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	@SuppressWarnings("unchecked")
	protected AggregateXCRecord process945(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{

		// Get the elements with the requested field
		List<Element> elements = transformMe.get945();

		// The subfields we're processing
		String targetCallNumberSubfields = "ab";
		String targetLocationDisplaySubfields = "l";
		String targetAudienceSubfields = "a";

		// If there were no matching elements, return the unmodified XC record
		if (elements.size() == 0)
			return transformInto;

		// Add each subfield to the specified level with the specified tag and
		// attribute
		for (Element element : elements) {

			// A StringBuilder to concat the values of all the subfields of the
			// Element
			StringBuilder callNumberBuilder = new StringBuilder();
			StringBuilder locationDisplayBuilder = new StringBuilder();
			StringBuilder audienceBuilder = new StringBuilder();

			// Get the subfields of the current element
			List<Element> subfields = element.getChildren("subfield",marcNamespace);

			boolean process = true;
			
			// Check if $5 is present as a subfield with institution code
			for (Element subfield : subfields) {
				
				if(		subfield.getAttribute("code").getValue().equals("5") &&
						subfield.getText().equals(getOrganizationCode()))
				
					// Do not process this field
					process = false;
					
			}
			
			// If $5 is not present
			if(process) {
				// Add each subfield to the specified level with the specified tag
				// and attribute
				for (Element subfield : subfields) {
	
					// Get the subfield's code
					String subfieldCode = subfield.getAttribute("code").getValue();
					
					if (targetCallNumberSubfields.contains(subfieldCode))
						callNumberBuilder.append(subfield.getText() + " ");
					
					if (targetLocationDisplaySubfields.contains(subfieldCode))
						locationDisplayBuilder.append(subfield.getText() + " ");
					
				}

				ArrayList<Element> holdingsElements = new ArrayList<Element>();

				if(callNumberBuilder.length() > 0)
				{
					// Create a xc:callNumber in a seperate holdings element based on the 945 ab values
					Element callNumberElement = new Element("callNumber",AggregateXCRecord.XC_NAMESPACE);
					holdingsElements.add(callNumberElement.setText(callNumberBuilder.toString().trim()));
				}
				
				if(locationDisplayBuilder.length() > 0)
				{
					
					// Create a xc:callNumber in a seperate holdings element based on the 945 ab values
					Element callNumberElement = new Element("location",AggregateXCRecord.XC_NAMESPACE);
					holdingsElements.add(callNumberElement.setText(locationDisplayBuilder.toString().trim()));
				}

				getXCRecordService().addHoldingsElement(transformInto, holdingsElements);
				
			} else {
				// Add each subfield to the specified level with the specified tag
				// and attribute
				for (Element subfield : subfields) {
	
					// Get the subfield's code
					String subfieldCode = subfield.getAttribute("code").getValue();
					
					if (targetAudienceSubfields.contains(subfieldCode))
						audienceBuilder.append(subfield.getText() + " ");
				}

				if(audienceBuilder.length() > 0)
				{
					getXCRecordService().addElement(transformInto, "audience", audienceBuilder.toString().trim(), AggregateXCRecord.DCTERMS_NAMESPACE, new ArrayList<Attribute>(), FrbrLevel.WORK);
				}
				
			}
				
		}

		return transformInto;

	}

	/**
	 * Processes the 947 field from the MarcXmlRecord we're transforming.
	 * The $a subfields become dcterms:identifier fields at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process947(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dcterms:identifier based on the 947 $a values
		return processFieldBasic(transformMe, transformInto, "947", 'a', "identifier", AggregateXCRecord.DCTERMS_NAMESPACE, new Attribute("type", "ISBN"), FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 959 field from the MarcXmlRecord we're transforming.
	 * The $a subfields become xc:creator fields in a work element specific to the
	 * 959 element's linking field.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	@SuppressWarnings("unchecked")
	protected AggregateXCRecord process959(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Get the elements with the requested tags in the MARC XML record
		List<Element> elements = transformMe.getDataFields("959");

		// If there were matching elements, return the unmodified XC record
		if(elements.size() == 0)
			return transformInto;

		linkedCreatorFields.clear();
		
		// Add each subfield to the specified level with the specified tag and attribute
		for(Element element : elements)
		{
			// A StringBuilder to concat the values of all the subfields of the Element
			StringBuilder builder = new StringBuilder();

			// Get the needed subfields of the element
			List<String> requestedAttributeSubfields = MarcXmlRecord.getSubfieldOfField(element, '0');
			List<String> linkingFields = MarcXmlRecord.getSubfieldOfField(element, '8');
			List<Element> subfields = element.getChildren("subfield", marcNamespace);

			// We're assuming the $0 subfield is not repeatable, so we just need to get the 1st one if it exists
			// If it does we'll use it's value as the attribute, otherwise we'll use defaultAttribute as the value
			String attributeValue = (requestedAttributeSubfields.size() > 0 ? requestedAttributeSubfields.get(0) : null);

			// Iterate over the subfields, and append each one to the StringBuilder if it
			// is in the list of target subfields
			for(Element subfield : subfields)
			{
				String code = subfield.getAttributeValue("code");

				// Don't append $0, $5, or $8 subfields
				if(!code.equals("0") && !code.equals("5") && !code.equals("8"))
					builder.append(subfield.getText() + " ");
			}

			// If any target fields were found
			if(builder.length() > 0)
			{
				String value = builder.substring(0, builder.length()-1); // The value is everything except the last space

				// Setup the attribute list for the XC record field
				ArrayList<Attribute> attributes = new ArrayList<Attribute>();
				if(attributeValue != null && attributeValue.contains("(") && attributeValue.contains(")"))
				{
					// Get the value and prefix of the control number
					String authValue = attributeValue.substring(attributeValue.indexOf(')') + 1);
					String prefix = attributeValue.substring(attributeValue.indexOf('(')+1, attributeValue.indexOf(')'));

					// If the prefix is "DLC" we should return an lcnaf attribute.
					// If it's the organization code we should return an xcauth attribute
					// Otherwise return null since we don't recognize the prefix
					if(prefix.equals("DLC"))
						attributes.add(new Attribute("agentID", "lcnaf:n" + authValue));
					else if(prefix.equals(getOrganizationCode()))
						attributes.add(new Attribute("agentID", "xcauth:" + authValue));
				}

				if(LOG.isDebugEnabled())
					LOG.debug("Adding a " + FrbrLevel.WORK + " level creator with an agentID of \"" + attributeValue + "\" based on the $a subfields' value, which is " + value);

				// Add the element to the XC record
				// Add to each non-default work element for which we found a linking field in the 959 element
				
				for(String linkingField : linkingFields)
				{
					Element creatorElement = new Element("creator", AggregateXCRecord.XC_NAMESPACE );
					creatorElement.setAttributes(attributes);
					creatorElement.addContent(value);
					linkedCreatorFields.put(linkingField, (Element)creatorElement.clone());
					
						//transformInto.addElementBasedOnLinkingField("creator", linkingField, XCRecord.XC_NAMESPACE, attributes, linkingField);
				}
			}
		}

		// Return the result
		return transformInto;
	}

	/**
	 * Processes the 963 field from the MarcXmlRecord we're transforming.
	 * The ay subfields becomes the xc:temporal field at the work FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process963(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Setup the map from the 2nd indicator to the type of the xc:subject
		HashMap<String, Attribute> indicatorToType = new HashMap<String, Attribute>();
		indicatorToType.put("0", new Attribute("type", "dcterms:LCSH", AggregateXCRecord.XSI_NAMESPACE));
		indicatorToType.put("1", new Attribute("type", "lcac"));
		indicatorToType.put("2", new Attribute("type", "dcterms:MESH", AggregateXCRecord.XSI_NAMESPACE));
		indicatorToType.put("3", new Attribute("type", "nal"));
		indicatorToType.put("5", new Attribute("type", "cash"));
		indicatorToType.put("6", new Attribute("type", "rvm"));
		indicatorToType.put("7", new Attribute("type", "$2"));

		// Create an xc:temporal based on the 963 ay values
		return processFieldWithAuthorityAttributeFromIndicator(transformMe, transformInto, "963", "ay", "", "temporal", AggregateXCRecord.XC_NAMESPACE, "2", indicatorToType, FrbrLevel.WORK);
	}

	/**
	 * Processes the 965 field from the MarcXmlRecord we're transforming.
	 * The $a subfield becomes the xc:subject field at the work FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process965(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Setup the map from the 2nd indicator to the type of the xc:subject
		HashMap<String, Attribute> indicatorToType = new HashMap<String, Attribute>();
		indicatorToType.put("0", new Attribute("type", "dcterms:LCSH", AggregateXCRecord.XSI_NAMESPACE));
		indicatorToType.put("1", new Attribute("type", "lcac"));
		indicatorToType.put("2", new Attribute("type", "dcterms:MESH", AggregateXCRecord.XSI_NAMESPACE));
		indicatorToType.put("3", new Attribute("type", "nal"));
		indicatorToType.put("5", new Attribute("type", "cash"));
		indicatorToType.put("6", new Attribute("type", "rvm"));
		indicatorToType.put("7", new Attribute("type", "$2"));

		// Create an xc:subject based on the 965 $a value
		return processFieldWithAuthorityAttributeFromIndicator(transformMe, transformInto, "965", "ax", "", "subject", AggregateXCRecord.XC_NAMESPACE, "2", indicatorToType, FrbrLevel.WORK);
	}

	/**
	 * Processes the 967 field from the MarcXmlRecord we're transforming.
	 * The $a subfield becomes the xc:spatial field at the work FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process967(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Setup the map from the 2nd indicator to the type of the xc:subject
		HashMap<String, Attribute> indicatorToType = new HashMap<String, Attribute>();
		indicatorToType.put("0", new Attribute("type", "dcterms:LCSH", AggregateXCRecord.XSI_NAMESPACE));
		indicatorToType.put("1", new Attribute("type", "lcac"));
		indicatorToType.put("2", new Attribute("type", "dcterms:MESH", AggregateXCRecord.XSI_NAMESPACE));
		indicatorToType.put("3", new Attribute("type", "nal"));
		indicatorToType.put("5", new Attribute("type", "cash"));
		indicatorToType.put("6", new Attribute("type", "rvm"));
		indicatorToType.put("7", new Attribute("type", "$2"));

		// Create an xc:spatial based on the 967 $a value
		return processFieldWithAuthorityAttributeFromIndicator(transformMe, transformInto, "967", "az", "", "spatial", AggregateXCRecord.XC_NAMESPACE, "2", indicatorToType, FrbrLevel.WORK);
	}

	/**
	 * Processes the 969 field from the MarcXmlRecord we're transforming.
	 * The $a subfield becomes the xc:type field at the work FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord process969(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Setup the map from the 2nd indicator to the type of the xc:subject
		HashMap<String, Attribute> indicatorToType = new HashMap<String, Attribute>();
		indicatorToType.put("0", new Attribute("type", "dcterms:LCSH", AggregateXCRecord.XSI_NAMESPACE));
		indicatorToType.put("1", new Attribute("type", "lcac"));
		indicatorToType.put("2", new Attribute("type", "dcterms:MESH", AggregateXCRecord.XSI_NAMESPACE));
		indicatorToType.put("3", new Attribute("type", "nal"));
		indicatorToType.put("5", new Attribute("type", "cash"));
		indicatorToType.put("6", new Attribute("type", "rvm"));
		indicatorToType.put("7", new Attribute("type", "$2"));

		// Create an xc:type based on the 969 $a value
		return processFieldWithAuthorityAttributeFromIndicator(transformMe, transformInto, "969", "av", "", "type", AggregateXCRecord.XC_NAMESPACE, "2", indicatorToType, FrbrLevel.WORK);
	}

	/**
	 * Adds an xc:recordId with the value from the 001.  If the 003 existed, its value is
	 * used as the type attribute, otherwise the type is set to the organization code from
	 * the configuration file.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord holdingsProcess001And003(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		String field001 = transformMe.getControlField("001");
		String field003 = transformMe.getControlField("003");
		if(field003 == null)
			field003 = getOrganizationCode();

		if(LOG.isDebugEnabled())
			LOG.debug("Adding a " + FrbrLevel.MANIFESTATION + " level recordId with a type of \"" + field003 + "\" and a value of " + field001);

		// Setup the attribute list
		ArrayList<Attribute> attributes = new ArrayList<Attribute>();
		attributes.add(new Attribute("type", field003));

		// Add the element to the XC record
		//transformInto.addElement("recordID", field001.trim(), XCRecord.XC_NAMESPACE, attributes, FrbrLevel.MANIFESTATION);

		if(LOG.isDebugEnabled())
			LOG.debug("Adding a " + FrbrLevel.HOLDINGS + " level recordId with a type of \"" + field003 + "\" and a value of " + field001);

		// Setup the attribute list
		attributes = new ArrayList<Attribute>();
		attributes.add(new Attribute("type", field003));

		// Add the element to the XC record
		getXCRecordService().addElement(transformInto, "recordID", field001.trim(), AggregateXCRecord.XC_NAMESPACE, attributes, FrbrLevel.HOLDINGS);

		// Return the result
		return transformInto;
	}

	/**
	 * Processes the 506 field from the MarcXmlRecord we're transforming.
	 * The abcdefu3 subfields become the dc:rights field at the manifestation FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord holdingsProcess506(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Create an dc:rights based on the 506 abcdefu3 values
		return processFieldBasic(transformMe, transformInto, "506", "abcdefu3", "rights", AggregateXCRecord.DCTERMS_NAMESPACE, null, FrbrLevel.MANIFESTATION);
	}

	/**
	 * Processes the 852 field from the MarcXmlRecord we're transforming.
	 * The b subfields become the xc:location field at the holdings FRBR level.
	 * The hijklm subfields become the xc:callNumber field at the holdings FRBR level.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	@SuppressWarnings("unchecked")
	protected AggregateXCRecord holdingsProcess852(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Get the elements with the requested tags in the MARC XML record
		List<Element> elements = transformMe.getDataFields("852");
		
		List<Element> relevantSiblings = new ArrayList();
		for (String df : new String[] {"866", "867", "868"}) {
			List<Element> sibs = transformMe.getDataFields(df);
			if (sibs != null && sibs.size() > 0)
				relevantSiblings.addAll(sibs);
		}

		// The subfields of the 852 datafield we're processing
		String locationTargetSubfields = "b";
		String callNumberTargetSubfields = "hijklm";
		String textualHoldingsTargetSubfields = "az";

		// If there were matching elements, return the unmodified XC record
		if(elements.size() == 0)
			return transformInto;

		// Add each subfield to the specified level with the specified tag and attribute
		for(Element element : elements)
		{
			// A StringBuilder to concat the values of all the subfields of the Element
			StringBuilder locationBuilder = new StringBuilder();
			StringBuilder callNumberBuilder = new StringBuilder();

			// Get the subfields of the current element
			List<Element> subfields = element.getChildren("subfield", marcNamespace);

			// Iterate over the subfields, and append each one to the StringBuilder if it
			// is in the list of target subfields
			for(Element subfield : subfields)
			{
				// Get the subfield's code
				String subfieldCode = subfield.getAttribute("code").getValue();

				if(locationTargetSubfields.contains(subfieldCode))
					locationBuilder.append(subfield.getText() + " ");
				if(callNumberTargetSubfields.contains(subfieldCode))
					callNumberBuilder.append(subfield.getText() + " ");
			}

			// A list of the values of the textual holdings elements, which are taken from
			// the 866, 867, and 868 datafields immediately following the current datafield.
			ArrayList<Element> textualHoldingsElements = new ArrayList<Element>();

			for (Element sibling : relevantSiblings) {
				String siblingTag = sibling.getAttributeValue("tag");
				
				StringBuilder textualHoldingsBuilder = new StringBuilder();

				// Get the subfields of the current element
				List<Element> siblingSubfields = sibling.getChildren("subfield", marcNamespace);

				// Iterate over the subfields, and append each one to the StringBuilder if it
				// is in the list of target subfields
				for(Element siblingSubfield : siblingSubfields)
				{
					// Get the subfield's code
					String siblingSubfieldCode = siblingSubfield.getAttributeValue("code");

					if(textualHoldingsTargetSubfields.contains(siblingSubfieldCode))
						textualHoldingsBuilder.append(siblingSubfield.getText() + " ");
				}

				if(textualHoldingsBuilder.length() > 0) {
					if (siblingTag.equals("866")) {
						textualHoldingsElements.add(new Element("textualHoldings", AggregateXCRecord.XC_NAMESPACE).setText(textualHoldingsBuilder.substring(0, textualHoldingsBuilder.length()-1)).setAttribute(new Attribute("type","Basic Bibliographic Unit")));
					}
					if (siblingTag.equals("867")) {
						textualHoldingsElements.add(new Element("textualHoldings", AggregateXCRecord.XC_NAMESPACE).setText(textualHoldingsBuilder.substring(0, textualHoldingsBuilder.length()-1)).setAttribute(new Attribute("type","Supplementary material")));
					}
					if (siblingTag.equals("868")) {
						textualHoldingsElements.add(new Element("textualHoldings", AggregateXCRecord.XC_NAMESPACE).setText(textualHoldingsBuilder.substring(0, textualHoldingsBuilder.length()-1)).setAttribute(new Attribute("type","Indexes")));
					}
				}
			}

			List<Element> holdingsContent = new ArrayList<Element>();

			String locationValue = (locationBuilder.length() > 0 ? locationBuilder.substring(0, locationBuilder.length()-1) : ""); // The value is everything except the last space
			String callNumberValue = (callNumberBuilder.length() > 0 ? callNumberBuilder.substring(0, callNumberBuilder.length()-1) : ""); // The value is everything except the last space

			if(locationValue.length() > 0)
				holdingsContent.add(new Element("location", AggregateXCRecord.XC_NAMESPACE).setText(locationValue));
			if(callNumberValue.length() > 0)
				holdingsContent.add(new Element("callNumber", AggregateXCRecord.XC_NAMESPACE).setText(callNumberValue));
			for(Element textualHoldingElement : textualHoldingsElements) {
				holdingsContent.add(textualHoldingElement);
			}
			
			getXCRecordService().addHoldingsElement(transformInto, holdingsContent);
		}

		// Return the result
		return transformInto;
	}

	/**
	 * Processes the 856 field from the MarcXmlRecord we're transforming.
	 * The abcdfhijklmnopqrstuvyz23 subfields become the a different field depending on the 2nd indicator.
	 * If 2nd indicator is 0, blank, or 8, map to holdings level dcterms:identifier
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	@SuppressWarnings("unchecked")
	protected AggregateXCRecord holdingsProcess856(MarcXmlRecord transformMe, AggregateXCRecord transformInto)
	{
		// Get the elements with the requested field
		List<Element> elements = transformMe.getDataFields("856");

		// The subfields we're processing
		String targetSubfields = "abcdfhijklmnopqrstuvyz23";

		// If there were no matching elements, return the unmodified XC record
		if(elements.size() == 0)
			return transformInto;

		// Add each subfield to the specified level with the specified tag and attribute
		for(Element element : elements)
		{
			// Get the required indicator
			String ind2 = MarcXmlRecord.getIndicatorOfField(element, "2");

			// Setup the attribute list for the processed field
			ArrayList<Attribute> attributes = new ArrayList<Attribute>();

			// A StringBuilder to concat the values of all the subfields of the Element
			StringBuilder builder = new StringBuilder();

			// Get the subfields of the current element
			List<Element> subfields = element.getChildren("subfield", marcNamespace);

			// Iterate over the subfields, and append each one to the StringBuilder if it
			// is in the list of target subfields
			for(Element subfield : subfields)
			{
				// Get the subfield's code
				String subfieldCode = subfield.getAttribute("code").getValue();

				if(targetSubfields.contains(subfieldCode))
					builder.append(subfield.getText() + " ");
			}

			// If any target fields were found
			if(builder.length() > 0)
			{
				String value = builder.substring(0, builder.length()-1); // The value is everything except the last space

				// If 2nd indicator is 0, blank, or 8, map to manifestation level dcterms:identifier
				if(ind2 == null || ind2.equals("0") || ind2.equals(" ") || ind2.equals("8"))
				{
					if(LOG.isDebugEnabled())
						LOG.debug("Adding a " + FrbrLevel.HOLDINGS + " level identifier based on the concatination of the 856's subfields' value, which is " + value);

					// Create an dcterms:identifier based on the 856 abcdfhijklmnopqrstuvwxyz23 values
					getXCRecordService().addElement(transformInto, "identifier", value.trim(), AggregateXCRecord.DCTERMS_NAMESPACE, attributes, FrbrLevel.HOLDINGS);
				}
			}
		}

		// Return the result
		return transformInto;
	}

	/**
	 * Process a data field from the MarcXmlRecord we're transforming and create the specified
	 * XC record field based on the datafield's value
	 *
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @param value The value of the field we're adding to the XC record
     * @param elementName The name of the XC element to create
     * @param elementNamespace The namespace of the element to create
     * @param elementAttribute The attribute to set on the element to create
     * @param level The FRBR level of the element to create
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord processFieldBasic(AggregateXCRecord transformInto, String value, String elementName, Namespace elementNamespace, Attribute elementAttribute, FrbrLevel level)
	{
		if(LOG.isDebugEnabled())
			LOG.debug("Adding a " + level + " level " + elementName + (elementAttribute == null ? "" : " with a " + elementAttribute.getName() + " of \"" + elementAttribute.getValue() + "\"") + " with a value of " + value);

		// Setup the attribute list
		ArrayList<Attribute> attributes = new ArrayList<Attribute>();
		if(elementAttribute != null)
			attributes.add((Attribute)elementAttribute.clone());

		// Add the element to the XC record
		getXCRecordService().addElement(transformInto, elementName, value.trim(), elementNamespace, attributes, level);

		// Return the result
		return transformInto;
	}

	/**
	 * Process a data field from the MarcXmlRecord we're transforming and create the specified
	 * XC record field based on the datafield's value
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @param field The tag for the datafield to process
     * @param subfield The subfield to process
     * @param elementName The name of the XC element to create
     * @param elementNamespace The namespace of the element to create
     * @param elementAttribute The attribute to set on the element to create
     * @param level The FRBR level of the element to create
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord processFieldBasic(MarcXmlRecord transformMe, AggregateXCRecord transformInto, 
			String field, char subfield, String elementName, Namespace elementNamespace, 
			Attribute elementAttribute, FrbrLevel level) {
		// Get the target subfields MARC XML record
		List<String> subfields = transformMe.getSubfield(field, subfield);
		
		// If there were no matching subfields return the unmodified XC record
		if(subfields == null || subfields.size() == 0)
			return transformInto;

		// Add each subfield to the specified level with the specified tag and attribute
		for(String value : subfields)
		{
			if(LOG.isDebugEnabled())
				LOG.debug("Adding a " + level + " level " + elementName + (elementAttribute == null ? "" : " with a " + 
						elementAttribute.getName() + " of \"" + elementAttribute.getValue() + "\"") + " based on the " + 
						field + " $" + subfield + " value, which is " + value);

			// Setup the attribute list
			ArrayList<Attribute> attributes = new ArrayList<Attribute>();
			if(elementAttribute != null)
				attributes.add((Attribute)elementAttribute.clone());
		
			// Add the element to the XC record
			getXCRecordService().addElement(transformInto, elementName, value.trim(), elementNamespace, attributes, level);
		}

		// Return the result
		return transformInto;
	}

	/**
	 * Process a data field from the MarcXmlRecord we're transforming and create the specified
	 * XC record field based on the datafield's value.  If there is only one subfield to map,
	 * the processFieldBasic method taking a char as the 4th parameter should be used instead of
	 * this one.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @param field The tag for the datafield to process
     * @param targetSubfields A String containing all the subfield to process
     * @param elementName The name of the XC element to create
     * @param elementNamespace The namespace of the element to create
     * @param elementAttribute The attribute to set on the element to create
     * @param level The FRBR level of the element to create
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	@SuppressWarnings("unchecked")
	protected AggregateXCRecord processFieldBasic(MarcXmlRecord transformMe, AggregateXCRecord transformInto, 
			String field, String targetSubfields, String elementName, Namespace elementNamespace, 
			Attribute elementAttribute, FrbrLevel level) {
		// Get the elements with the requested tags in the MARC XML record
		List<Element> elements = transformMe.getDataFields(field);

		// If there were matching elements, return the unmodified XC record
		if(elements.size() == 0)
			return transformInto;

		// Add each subfield to the specified level with the specified tag and attribute
		for(Element element : elements)
		{
			// A StringBuilder to concat the values of all the subfields of the Element
			StringBuilder builder = new StringBuilder();

			// Get the subfields of the current element
			List<Element> subfields = element.getChildren("subfield", marcNamespace);

			// Iterate over the subfields, and append each one to the StringBuilder if it
			// is in the list of target subfields
			for(Element subfield : subfields)
			{
				// Get the subfield's code
				String subfieldCode = subfield.getAttribute("code").getValue();

				if(targetSubfields.contains(subfieldCode))
					builder.append(subfield.getText()).append(" ");
			}

			// If any target fields were found
			if(builder.length() > 0)
			{
				String value = builder.substring(0, builder.length()-1); // The value is everything except the last space

				if(LOG.isDebugEnabled())
					LOG.debug("Adding a " + level + " level " + elementName + (elementAttribute == null ? "" : " with a " + 
							elementAttribute.getName() + " of \"" + elementAttribute.getValue() + "\"") + 
							" based on the concatination of the " + field + "'s subfields' value, which is " + value);

				// Setup the attribute list
				ArrayList<Attribute> attributes = new ArrayList<Attribute>();
				if(elementAttribute != null)
					attributes.add((Attribute)elementAttribute.clone());

				// Add the element to the XC record
				getXCRecordService().addElement(transformInto, elementName, value.trim(), 
						elementNamespace, attributes, level);
			}
		}

		// Return the result
		return transformInto;
	}

	/**
	 * Process a data field from the MarcXmlRecord we're transforming and create the specified
	 * XC record field based on the datafield's value.  Subfield codes are replaced with dashes
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @param field The tag for the datafield to process
     * @param targetSubfields A String containing all the subfield to process
     * @param elementName The name of the XC element to create
     * @param elementNamespace The namespace of the element to create
     * @param elementAttribute The attribute to set on the element to create
     * @param level The FRBR level of the element to create
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	@SuppressWarnings("unchecked")
	protected AggregateXCRecord processFieldBasicDashSubfields(MarcXmlRecord transformMe, AggregateXCRecord transformInto, String field, String targetSubfields, String elementName, Namespace elementNamespace, Attribute elementAttribute, FrbrLevel level)
	{
		// Get the elements with the requested tags in the MARC XML record
		List<Element> elements = transformMe.getDataFields(field);

		// If there were matching elements, return the unmodified XC record
		if(elements.size() == 0)
			return transformInto;

		// Add each subfield to the specified level with the specified tag and attribute
		for(Element element : elements)
		{
			// A StringBuilder to concat the values of all the subfields of the Element
			StringBuilder builder = new StringBuilder();

			// Get the subfields of the current element
			List<Element> subfields = element.getChildren("subfield", marcNamespace);

			// Iterate over the subfields, and append each one to the StringBuilder if it
			// is in the list of target subfields
			for(Element subfield : subfields)
			{
				// Get the subfield's code
				String subfieldCode = subfield.getAttribute("code").getValue();

				if(targetSubfields.contains(subfieldCode))
					builder.append(subfield.getText() + "-");
			}

			// If any target fields were found
			if(builder.length() > 0)
			{
				String value = builder.substring(0, builder.length()-1); // The value is everything except the last space

				if(LOG.isDebugEnabled())
					LOG.debug("Adding a " + level + " level " + elementName + (elementAttribute == null ? "" : " with a " + elementAttribute.getName() + " of \"" + elementAttribute.getValue() + "\"") + " based on the concatination of the " + field + "'s subfields' value, which is " + value);

				// Setup the attribute list
				ArrayList<Attribute> attributes = new ArrayList<Attribute>();
				if(elementAttribute != null)
					attributes.add((Attribute)elementAttribute.clone());

				// Add the element to the XC record
				getXCRecordService().addElement(transformInto, elementName, value.trim(), elementNamespace, attributes, level);
			}
		}

		// Return the result
		return transformInto;
	}

	/**
	 * Process a data field from the MarcXmlRecord we're transforming and create the specified
	 * XC record field based on the datafield's value
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @param field The tag for the datafield to process
     * @param subfield The subfield to process.
     * @param elementName The name of the XC element to create
     * @param elementNamespace The namespace of the element to create
     * @param elementAttribute The attribute to set on the element to create.  This subfield is assumed to be non-repeating within the requested field.
     * @param attributeSubfield The subfield to take the attribute's value from
     * @param defaultAttribute The default value of the attribute.  The attribute will take this value if the attributeSubfield was not found.
     * @param level The FRBR level of the element to create
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord processFieldAttributeFromSubfield(MarcXmlRecord transformMe, AggregateXCRecord transformInto, String field, char subfield, String elementName, Namespace elementNamespace, Attribute elementAttribute, char attributeSubfield, String defaultAttribute, FrbrLevel level)
	{
		// Get the elements with the requested tags in the MARC XML record
		List<Element> elements = transformMe.getDataFields(field);

		// If there were matching elements, return the unmodified XC record
		if(elements.size() == 0)
			return transformInto;

		// Add each subfield to the specified level with the specified tag and attribute
		for(Element element : elements)
		{
			// Get the needed subfields of the element
			List<String> requestedSubfields = MarcXmlRecord.getSubfieldOfField(element, subfield);
			List<String> requestedAttributeSubfields = MarcXmlRecord.getSubfieldOfField(element, attributeSubfield);

			// If there were were no matches for the requested subfield, we have no data to transform.  In this
			// case continue to the next element
			if(requestedSubfields.size() <= 0)
				continue;

			// We're assuming the attribute subfield is not repeatable, so we just need to get the 1st one if it exists
			// If it does we'll use it's value as the attribute, otherwise we'll use defaultAttribute as the value
			String attributeValue = (requestedAttributeSubfields.size() > 0 ? requestedAttributeSubfields.get(0) : defaultAttribute);

			// Loop over the values of the requested subfields and add the specified element for each
			for(String value : requestedSubfields)
			{
				// Setup the attribute list for the XC record field
				ArrayList<Attribute> attributes = new ArrayList<Attribute>();
				if(elementAttribute != null && attributeValue != null)
					attributes.add(((Attribute)elementAttribute.clone()).setValue(attributeValue));

				if(LOG.isDebugEnabled())
					LOG.debug("Adding a " + level + " level " + elementName + (elementAttribute == null ? "" : " with a " + elementAttribute.getName() + " of \"" + attributeValue + "\"") + " based on the " + field + " $" + subfield + " value, which is " + value);

				// Add the element to the XC record
				getXCRecordService().addElement(transformInto, elementName, value.trim(), elementNamespace, attributes, level);
			}
		}

		// Return the result
		return transformInto;
	}

	/**
	 * Process a data field from the MarcXmlRecord we're transforming and create the specified
	 * XC record field based on the datafield's value
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @param field The tag for the datafield to process
     * @param subfield The subfield to process.
     * @param elementName The name of the XC element to create
     * @param elementNamespace The namespace of the element to create
     * @param elementAttribute The attribute to set on the element to create.  This subfield is assumed to be non-repeating within the requested field.
     * @param attributeSubfield The subfield to take the attribute's value from
     * @param defaultAttribute The default value of the attribute.  The attribute will take this value if the attributeSubfield was not found.
     * @param level The FRBR level of the element to create
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	@SuppressWarnings("unchecked")
	protected AggregateXCRecord processFieldAttributeFromSubfield(MarcXmlRecord transformMe, AggregateXCRecord transformInto, String field, String targetSubfields, String elementName, Namespace elementNamespace, Attribute elementAttribute, char attributeSubfield, String defaultAttribute, FrbrLevel level)
	{
		// Get the elements with the requested tags in the MARC XML record
		List<Element> elements = transformMe.getDataFields(field);

		// If there were matching elements, return the unmodified XC record
		if(elements.size() == 0)
			return transformInto;

		// Add each subfield to the specified level with the specified tag and attribute
		for(Element element : elements)
		{
			// Get the needed subfields of the element
			List<String> requestedAttributeSubfields = MarcXmlRecord.getSubfieldOfField(element, attributeSubfield);

			// We're assuming the attribute subfield is not repeatable, so we just need to get the 1st one if it exists
			// If it does we'll use it's value as the attribute, otherwise we'll use defaultAttribute as the value
			String attributeValue = (requestedAttributeSubfields.size() > 0 ? requestedAttributeSubfields.get(0) : defaultAttribute);

			// A StringBuilder to concat the values of all the subfields of the Element
			StringBuilder builder = new StringBuilder();

			// Get the subfields of the current element
			List<Element> subfields = element.getChildren("subfield", marcNamespace);

			// Iterate over the subfields, and append each one to the StringBuilder if it
			// is in the list of target subfields
			for(Element subfield : subfields)
			{
				// Get the subfield's code
				String subfieldCode = subfield.getAttribute("code").getValue();

				if(targetSubfields.contains(subfieldCode))
					builder.append(subfield.getText() + " ");
			}

			// If any target fields were found
			if(builder.length() > 0)
			{
				String value = builder.substring(0, builder.length()-1); // The value is everything except the last space

				if(LOG.isDebugEnabled())
					LOG.debug("Adding a " + level + " level " + elementName + (elementAttribute == null ? "" : " with a " + elementAttribute.getName() + " of \"" + elementAttribute.getValue() + "\"") + " based on the concatination of the " + field + "'s subfields' value, which is " + value);

				// Setup the attribute list for the XC record field
				ArrayList<Attribute> attributes = new ArrayList<Attribute>();
				if(elementAttribute != null && attributeValue != null)
					attributes.add(((Attribute)elementAttribute.clone()).setValue(attributeValue));

				// Add the element to the XC record
				getXCRecordService().addElement(transformInto, elementName, value.trim(), elementNamespace, attributes, level);
			}
		}

		// Return the result
		return transformInto;
	}

	/**
	 * Process a data field from the MarcXmlRecord we're transforming and create the specified
	 * XC record field based on the datafield's value
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @param field The tag for the datafield to process
     * @param subfield The subfield to process.
     * @param elementName The name of the XC element to create
     * @param elementNamespace The namespace of the element to create
     * @param elementAttribute The attribute to set on the element to create.  This subfield is assumed to be non-repeating within the requested field.
     * @param attributeSubfield The subfield to take the attribute's value from
     * @param defaultAttribute The default value of the attribute.  The attribute will take this value if the attributeSubfield was not found.
     * @param level The FRBR level of the element to create
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	@SuppressWarnings("unchecked")
	protected AggregateXCRecord processFieldAttributeFromSubfield(MarcXmlRecord transformMe, AggregateXCRecord transformInto, String field, String targetSubfields, String dashSubfields, String elementName, Namespace elementNamespace, Attribute elementAttribute, char attributeSubfield, String defaultAttribute, FrbrLevel level)
	{
		// Get the elements with the requested tags in the MARC XML record
		List<Element> elements = transformMe.getDataFields(field);

		// If there were matching elements, return the unmodified XC record
		if(elements.size() == 0)
			return transformInto;

		// Add each subfield to the specified level with the specified tag and attribute
		for(Element element : elements)
		{
			// Get the needed subfields of the element
			List<String> requestedAttributeSubfields = MarcXmlRecord.getSubfieldOfField(element, attributeSubfield);

			// We're assuming the attribute subfield is not repeatable, so we just need to get the 1st one if it exists
			// If it does we'll use it's value as the attribute, otherwise we'll use defaultAttribute as the value
			String attributeValue = (requestedAttributeSubfields.size() > 0 ? requestedAttributeSubfields.get(0) : defaultAttribute);

			// A StringBuilder to concat the values of all the subfields of the Element
			StringBuilder builder = new StringBuilder();

			// Get the subfields of the current element
			List<Element> subfields = element.getChildren("subfield", marcNamespace);

			// Iterate over the subfields, and append each one to the StringBuilder if it
			// is in the list of target subfields
			for(Element subfield : subfields)
			{
				// Get the subfield's code
				String subfieldCode = subfield.getAttribute("code").getValue();

				if(targetSubfields.contains(subfieldCode))
				{
					if(dashSubfields.contains(subfieldCode) && builder.length() > 0)
					{
						builder.setCharAt(builder.length()-1, '-');
						builder.append('-' + subfield.getText() + " ");
					}
					else
						builder.append(subfield.getText() + " ");
				}
			}

			// If any target fields were found
			if(builder.length() > 0)
			{
				String value = builder.substring(0, builder.length()-1); // The value is everything except the last space

				if(LOG.isDebugEnabled())
					LOG.debug("Adding a " + level + " level " + elementName + (elementAttribute == null ? "" : " with a " + elementAttribute.getName() + " of \"" + elementAttribute.getValue() + "\"") + " based on the concatination of the " + field + "'s subfields' value, which is " + value);

				// Setup the attribute list for the XC record field
				ArrayList<Attribute> attributes = new ArrayList<Attribute>();
				if(elementAttribute != null && attributeValue != null)
					attributes.add(((Attribute)elementAttribute.clone()).setValue(attributeValue));

				// Add the element to the XC record
				getXCRecordService().addElement(transformInto, elementName, value.trim(), elementNamespace, attributes, level);
			}
		}

		// Return the result
		return transformInto;
	}

	/**
	 * Process a data field from the MarcXmlRecord we're transforming and create the specified
	 * XC record field based on the datafield's value
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @param field The tag for the datafield to process
     * @param subfield The subfield to process.
     * @param elementName The name of the XC element to create
     * @param elementNamespace The namespace of the element to create
     * @param subfieldToAttribute A map from subfields to the attribute their value should be stored in on the processed element
     * @param level The FRBR level of the element to create
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	@SuppressWarnings("unchecked")
	protected AggregateXCRecord processFieldAttributeFromSubfield(MarcXmlRecord transformMe, AggregateXCRecord transformInto, String field, String targetSubfields, String elementName, Namespace elementNamespace, HashMap<String, Attribute> subfieldToAttribute, FrbrLevel level)
	{
		// Get the elements with the requested tags in the MARC XML record
		List<Element> elements = transformMe.getDataFields(field);

		// If there were matching elements, return the unmodified XC record
		if(elements.size() == 0)
			return transformInto;

		// Add each subfield to the specified level with the specified tag and attribute
		for(Element element : elements)
		{
			// A StringBuilder to concat the values of all the subfields of the Element
			StringBuilder builder = new StringBuilder();

			// Setup the attribute list for the XC record field
			ArrayList<Attribute> attributes = new ArrayList<Attribute>();

			// Get the subfields of the current element
			List<Element> subfields = element.getChildren("subfield", marcNamespace);

			// Iterate over the subfields, and append each one to the StringBuilder if it
			// is in the list of target subfields
			for(Element subfield : subfields)
			{
				// Get the subfield's code
				String subfieldCode = subfield.getAttribute("code").getValue();

				// Append the subfield's value to the processed element's value if is one of the subfields we're processing
				if(targetSubfields.contains(subfieldCode))
					builder.append(subfield.getText() + " ");

				// If the subfield is one we're mapping to an attribute, add the attribute to the list of
				// attributes to set on the processed element after setting the attribute's value correctly
				if(subfieldToAttribute.containsKey(subfieldCode))
				{
					Attribute attributeToAdd = (Attribute)subfieldToAttribute.get(subfieldCode).clone();
					attributeToAdd.setValue(subfield.getText());
					attributes.add(attributeToAdd);
				}
			}

			// If any target fields were found
			if(builder.length() > 0)
			{
				String value = builder.substring(0, builder.length()-1); // The value is everything except the last space

				if(LOG.isDebugEnabled())
					LOG.debug("Adding a " + level + " level " + elementName + " based on the concatination of the " + field + "'s subfields' value, which is " + value);

				// Add the element to the XC record
				getXCRecordService().addElement(transformInto, elementName, value.trim(), elementNamespace, attributes, level);
			}
		}

		// Return the result
		return transformInto;
	}

	/**
	 * Process a data field from the MarcXmlRecord we're transforming and create the specified
	 * XC record field based on the datafield's value.  All data fields for which the $4 subfield
	 * is a known role are not processed.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @param field The tag for the datafield to process
     * @param targetSubfields The subfield to process.
     * @param elementName The name of the XC element to create
     * @param elementNamespace The namespace of the element to create
     * @param level The FRBR level of the element to create
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	@SuppressWarnings("unchecked")
	protected AggregateXCRecord processFieldWithAuthority(MarcXmlRecord transformMe, AggregateXCRecord transformInto, String field, String targetSubfields, String elementName, Namespace elementNamespace, FrbrLevel level)
	{
		// Get the elements with the requested tags in the MARC XML record
		List<Element> elements = transformMe.getDataFields(field);

		// If there were matching elements, return the unmodified XC record
		if(elements.size() == 0)
			return transformInto;

		// Add each subfield to the specified level with the specified tag and attribute
		for(Element element : elements)
		{
			// A StringBuilder to concat the values of all the subfields of the Element
			StringBuilder builder = new StringBuilder();

			// Get the needed subfields of the element
			List<String> requestedAttributeSubfields = MarcXmlRecord.getSubfieldOfField(element, '0');
			List<Element> subfields = element.getChildren("subfield", marcNamespace);

			// We're assuming the attribute subfield is not repeatable, so we just need to get the 1st one if it exists
			// If it does we'll use it's value as the attribute, otherwise we'll use defaultAttribute as the value
			Attribute authorityAttribute = null;
			for(String requestedAttributeSubfield : requestedAttributeSubfields)
			{
				authorityAttribute = getAttributeForAuthority(field, requestedAttributeSubfield);
				if(authorityAttribute != null)
					break;
			}

			// Iterate over the subfields, and append each one to the StringBuilder if it
			// is in the list of target subfields
			for(Element subfield : subfields)
			{
				// Get the subfield's code
				String subfieldCode = subfield.getAttribute("code").getValue();

				if(targetSubfields.contains(subfieldCode))
					builder.append(subfield.getText() + " ");
			}

			// If any target fields were found
			if(builder.length() > 0)
			{
				String value = builder.substring(0, builder.length()-1); // The value is everything except the last space

				// Setup the attribute list for the XC record field
				ArrayList<Attribute> attributes = new ArrayList<Attribute>();
				if(authorityAttribute != null)
					attributes.add(authorityAttribute);

				if(LOG.isDebugEnabled())
					LOG.debug("Adding a " + level + " level " + elementName + (authorityAttribute == null ? "" : " with a " + authorityAttribute.getName() + " of \"" + authorityAttribute.getValue() + "\"") + " based on the concatination of the " + field + "'s subfields' value, which is " + value);

				// Add the element to the XC record
				getXCRecordService().addElement(transformInto, elementName, value.trim(), elementNamespace, attributes, level);
			}
		}

		// Return the result
		return transformInto;
	}

	/**
	 * Process a data field from the MarcXmlRecord we're transforming and create the specified
	 * XC record field based on the datafield's value.  All data fields for which the $4 subfield
	 * is a known role are not processed.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @param field The tag for the datafield to process
     * @param targetSubfields The subfield to process.
     * @param elementName The name of the XC element to create
     * @param elementNamespace The namespace of the element to create
     * @param level The FRBR level of the element to create
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	@SuppressWarnings("unchecked")
	protected AggregateXCRecord processFieldWithAuthorityAttributeFromIndicator(MarcXmlRecord transformMe, AggregateXCRecord transformInto, 
			String field, String targetSubfields, String subfieldsToDash, String elementName, Namespace elementNamespace, 
			String targetIndicator, HashMap<String, Attribute> indicatorToValue, FrbrLevel level)
	{
		// Get the elements with the requested tags in the MARC XML record
		List<Element> elements = transformMe.getDataFields(field);
		
		// If there were matching elements, return the unmodified XC record
		if(elements.size() == 0)
			return transformInto;

		// Add each subfield to the specified level with the specified tag and attribute
		for(Element element : elements)
		{
			// Get the requested indicator
			String indicator = MarcXmlRecord.getIndicatorOfField(element, targetIndicator);
			// If the requested subfield or indicator were not found, continue to the next element
			// Also continue to the next element if there was no attribute value for the value of the indicator
			if(indicator == null || indicator.length() <= 0 || !indicatorToValue.containsKey(indicator))
				continue;

			// A StringBuilder to concat the values of all the subfields of the Element
			StringBuilder builder = new StringBuilder();

			// Get the needed subfields of the element
			List<String> requestedAttributeSubfields = MarcXmlRecord.getSubfieldOfField(element, '0');
			List<Element> subfields = element.getChildren("subfield", marcNamespace);

			// We're assuming the attribute subfield is not repeatable, so we just need to get the 1st one if it exists
			// If it does we'll use it's value as the attribute, otherwise we'll use defaultAttribute as the value
			Attribute authorityAttribute = null;
			for(String requestedAttributeSubfield : requestedAttributeSubfields)
			{
				authorityAttribute = getAttributeForAuthority(field, requestedAttributeSubfield);
				if(authorityAttribute != null)
					break;
			}

			// Iterate over the subfields, and append each one to the StringBuilder if it
			// is in the list of target subfields
			for(Element subfield : subfields)
			{
				// Get the subfield's code
				String subfieldCode = subfield.getAttribute("code").getValue();
				if(targetSubfields.contains(subfieldCode))
				{
					if(subfieldsToDash.contains(subfieldCode))
					{
						if (builder.length() > 0) {
							builder.setCharAt(builder.length()-1, '-');
						}
						builder.append(subfield.getText() + " ");
					}
					else {
						builder.append(subfield.getText() + " ");
					}
				}
			}

			// If any target fields were found
			if(builder.length() > 0)
			{
				String value = builder.substring(0, builder.length()-1); // The value is everything except the last space

				// Get the attribute value based on the indicator
				Attribute attributeToUse = (Attribute)indicatorToValue.get(indicator).clone();

				// If the attribute indicates its value is taken from a subfield, set it correctly
				if(attributeToUse.getValue().startsWith("$"))
				{
					// Get a list of possible value of the attributes subfield
					List<String> attSubfieldValues = MarcXmlRecord.getSubfieldOfField(element, attributeToUse.getValue().charAt(1));

					// Use the first possible value if there were any
					if(attSubfieldValues.size() > 0)
						attributeToUse.setValue(attSubfieldValues.get(0));
				}

				// Setup the attribute list for the XC record field
				ArrayList<Attribute> attributes = new ArrayList<Attribute>();
				if(attributeToUse != null)
					attributes.add(attributeToUse);

				// Add the authority attribute to the list
				if(authorityAttribute != null)
					attributes.add(authorityAttribute);

				if(LOG.isDebugEnabled())
					LOG.debug("Adding a " + level + " level " + elementName + (attributeToUse != null ? " with a " + attributeToUse.getName() + " of \"" + attributeToUse.getValue() + "\"" : "") + (authorityAttribute != null ? " with a " + authorityAttribute.getName() + " of \"" + authorityAttribute.getValue() + "\"" : "") + " based on the concatination of the " + field + "'s subfields' value, which is " + value);

				// Add the element to the XC record
				getXCRecordService().addElement(transformInto, elementName, value.trim(), elementNamespace, attributes, level);
			}
		}

		// Return the result
		return transformInto;
	}

	/**
	 * Process a data field from the MarcXmlRecord we're transforming and create the specified
	 * XC record field based on the datafield's value.  All data fields for which the $4 subfield
	 * is a known role are not processed.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @param field The tag for the datafield to process
     * @param targetSubfields The subfield to process.
     * @param elementName The name of the XC element to create
     * @param elementNamespace The namespace of the element to create
     * @param level The FRBR level of the element to create
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	@SuppressWarnings("unchecked")
	protected AggregateXCRecord processFieldWithAuthorityAttributeFromSubfield(MarcXmlRecord transformMe, AggregateXCRecord transformInto, String field, String targetSubfields, String elementName, Namespace elementNamespace, Attribute attribute, char attributeSubfield, FrbrLevel level)
	{
		// Get the elements with the requested tags in the MARC XML record
		List<Element> elements = transformMe.getDataFields(field);

		// If there were matching elements, return the unmodified XC record
		if(elements.size() == 0)
			return transformInto;

		// Add each subfield to the specified level with the specified tag and attribute
		for(Element element : elements)
		{
			// Get the requested indicator
			List<String> attSubfieldValues = MarcXmlRecord.getSubfieldOfField(element, attributeSubfield);

			// A StringBuilder to concat the values of all the subfields of the Element
			StringBuilder builder = new StringBuilder();

			// Get the needed subfields of the element
			List<String> requestedAttributeSubfields = MarcXmlRecord.getSubfieldOfField(element, '0');
			List<Element> subfields = element.getChildren("subfield", marcNamespace);

			// We're assuming the attribute subfield is not repeatable, so we just need to get the 1st one if it exists
			// If it does we'll use it's value as the attribute, otherwise we'll use defaultAttribute as the value
			Attribute authorityAttribute = null;
			for(String requestedAttributeSubfield : requestedAttributeSubfields)
			{
				authorityAttribute = getAttributeForAuthority(field, requestedAttributeSubfield);
				if(authorityAttribute != null)
					break;
			}

			// Iterate over the subfields, and append each one to the StringBuilder if it
			// is in the list of target subfields
			for(Element subfield : subfields)
			{
				// Get the subfield's code
				String subfieldCode = subfield.getAttribute("code").getValue();

				if(targetSubfields.contains(subfieldCode))
					builder.append(subfield.getText() + " ");
			}

			// If any target fields were found
			if(builder.length() > 0)
			{
				String value = builder.substring(0, builder.length()-1); // The value is everything except the last space

				// Get the attribute value based on the indicator
				Attribute attributeToUse = null;

				if(attSubfieldValues.size() > 0)
					attributeToUse = ((Attribute)attribute.clone()).setValue(attSubfieldValues.get(0));

				// Setup the attribute list for the XC record field
				ArrayList<Attribute> attributes = new ArrayList<Attribute>();
				if(attributeToUse != null)
					attributes.add(attributeToUse);

				// Add the authority attribute to the list
				if(authorityAttribute != null)
					attributes.add(authorityAttribute);

				if(LOG.isDebugEnabled())
					LOG.debug("Adding a " + level + " level " + elementName + (attributeToUse != null ? " with a " + attributeToUse.getName() + " of \"" + attributeToUse.getValue() + "\"" : "") + (authorityAttribute != null ? " with a " + authorityAttribute.getName() + " of \"" + authorityAttribute.getValue() + "\"" : "") + " based on the concatination of the " + field + "'s subfields' value, which is " + value);

				// Add the element to the XC record
				getXCRecordService().addElement(transformInto, elementName, value.trim(), elementNamespace, attributes, level);
			}
		}

		// Return the result
		return transformInto;
	}

	/**
	 * Process a data field from the MarcXmlRecord we're transforming and create the specified
	 * XC record field based on the datafield's value.  All data fields for which the $4 subfield
	 * is a known role are not processed.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @param field The tag for the datafield to process
     * @param targetSubfields The subfield to process.
     * @param elementName The name of the XC element to create
     * @param elementNamespace The namespace of the element to create
     * @param level The FRBR level of the element to create
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	@SuppressWarnings("unchecked")
	protected AggregateXCRecord processFieldWithAuthorityIgnoreRoles(MarcXmlRecord transformMe, AggregateXCRecord transformInto, String field, String targetSubfields, String defaultElementName, Namespace defaultElementNamespace, FrbrLevel defaultLevel)
	{
		// Get the elements with the requested tags in the MARC XML record
		List<Element> elements = transformMe.getDataFields(field);

		// If there were matching elements, return the unmodified XC record
		if(elements.size() == 0)
			return transformInto;

		// Add each subfield to the specified level with the specified tag and attribute
		for(Element element : elements)
		{
			// The element name and namespace
			String elementName = defaultElementName;
			Namespace elementNamespace = defaultElementNamespace;
			FrbrLevel level = defaultLevel;

			// A StringBuilder to concat the values of all the subfields of the Element
			StringBuilder builder = new StringBuilder();

			// Get the needed subfields of the element
			List<String> roleSubfields = MarcXmlRecord.getSubfieldOfField(element, '4');
			List<String> requestedAttributeSubfields = MarcXmlRecord.getSubfieldOfField(element, '0');
			List<Element> subfields = element.getChildren("subfield", marcNamespace);

			// We're assuming the attribute subfield is not repeatable, so we just need to get the 1st one if it exists
			// If it does we'll use it's value as the attribute, otherwise we'll use defaultAttribute as the value
			Attribute authorityAttribute = null;
			for(String requestedAttributeSubfield : requestedAttributeSubfields)
			{
				authorityAttribute = getAttributeForAuthority(field, requestedAttributeSubfield);
				if(authorityAttribute != null)
					break;
			}

			// Iterate over the subfields, and append each one to the StringBuilder if it
			// is in the list of target subfields
			for(Element subfield : subfields)
			{
				// Get the subfield's code
				String subfieldCode = subfield.getAttribute("code").getValue();

				if(targetSubfields.contains(subfieldCode))
					builder.append(subfield.getText() + " ");
			}

			// If any target fields were found
			if(builder.length() > 0)
			{
				String value = builder.substring(0, builder.length()-1); // The value is everything except the last space

				if(roleSubfields.size() <= 0)
				{
					// Setup the attribute list for the XC record field
					ArrayList<Attribute> attributes = new ArrayList<Attribute>();
					if(authorityAttribute != null)
						attributes.add(authorityAttribute);

					if(LOG.isDebugEnabled())
						LOG.debug("Adding a " + level + " level " + elementName + (authorityAttribute == null ? "" : " with an agentID of \"" + authorityAttribute.getValue() + "\"") + " based on the concatination of the " + field + "'s subfields' value, which is " + value);

					// Add the element to the XC record
					getXCRecordService().addElement(transformInto, elementName, value.trim(), elementNamespace, attributes, level);
				}
				else
				{
					elementNamespace = AggregateXCRecord.RDAROLE_NAMESPACE;

					boolean addedRole = false;

					for(String roleSubfield : roleSubfields)
					{
						if(roleSubfield.length() < 3 || !roles.containsKey(roleSubfield.substring(0, 3)))
							continue;

						elementName = roles.get(roleSubfield.substring(0, 3));

						if(elementName.equals("director") || elementName.equals("editor") || elementName.equals("ilustrator") || elementName.equals("performer") || elementName.equals("producer") || elementName.equals("translator"))
							level = FrbrLevel.EXPRESSION;

						// Setup the attribute list for the XC record field
						ArrayList<Attribute> attributes = new ArrayList<Attribute>();
						if(authorityAttribute != null)
							attributes.add((Attribute)authorityAttribute.clone());

						if(LOG.isDebugEnabled())
							LOG.debug("Adding a " + level + " level " + elementName + (authorityAttribute == null ? "" : " with an agentID of \"" + authorityAttribute.getValue() + "\"") + " based on the concatination of the " + field + "'s subfields' value, which is " + value);

						// Add the element to the XC record
						getXCRecordService().addElement(transformInto, elementName, value.trim(), elementNamespace, attributes, level);

						addedRole = true;
					}

					if(!addedRole)
					{
						elementNamespace = defaultElementNamespace;
						elementName = defaultElementName;

						// Setup the attribute list for the XC record field
						ArrayList<Attribute> attributes = new ArrayList<Attribute>();
						if(authorityAttribute != null)
							attributes.add((Attribute)authorityAttribute.clone());

						if(LOG.isDebugEnabled())
							LOG.debug("Adding a " + level + " level " + elementName + (authorityAttribute == null ? "" : " with an agentID of \"" + authorityAttribute.getValue() + "\"") + " based on the concatination of the " + field + "'s subfields' value, which is " + value);

						// Add the element to the XC record
						getXCRecordService().addElement(transformInto, elementName, value.trim(), elementNamespace, attributes, level);
					}
				}
			}
		}

		// Return the result
		return transformInto;
	}

	/**
	 * Process a data field from the MarcXmlRecord we're transforming and create the specified
	 * XC record field based on the datafield's value
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @param field The tag for the datafield to process
     * @param subfield The subfield to process
     * @param elementName The name of the XC element to create
     * @param elementNamespace The namespace of the element to create
     * @param elementAttribute The attribute to set on the element to create
     * @param targetIndicator The indicator to base the attribute's value on
     * @param indicatorToValue A map from the indicator value to the desired value of the attribute
     * @param level The FRBR level of the element to create
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	protected AggregateXCRecord processFieldAttributeFromIndicator(MarcXmlRecord transformMe, AggregateXCRecord transformInto, String field, char subfield, String elementName, Namespace elementNamespace, String targetIndicator, HashMap<String, Attribute> indicatorToValue, boolean requireIndicator, FrbrLevel level)
	{
		// Get the elements with the requested field
		List<Element> elements = transformMe.getDataFields(field);

		// If there were no matching elements, return the unmodified XC record
		if(elements.size() == 0)
			return transformInto;

		// Add each subfield to the specified level with the specified tag and attribute
		for(Element element : elements)
		{
			// Get the target subfields and indicator from the datafield
			List<String> subfields = MarcXmlRecord.getSubfieldOfField(element, subfield);
			String indicator = MarcXmlRecord.getIndicatorOfField(element, targetIndicator);

			// If the requested subfield or indicator were not found, continue to the next element
			// Also continue to the next element if there was no attribute value for the value of the indicator
			if(subfields.size() <= 0 || (requireIndicator && (indicator == null || indicator.length() <= 0 || !indicatorToValue.containsKey(indicator))))
				continue;

			// Get the attribute value based on the indicator
			Attribute attributeToUse = (indicatorToValue.get(indicator) == null ? null : (Attribute)indicatorToValue.get(indicator).clone());

			// If the attribute indicates its value is taken from a subfield, set it correctly
			if(attributeToUse != null && attributeToUse.getValue().startsWith("$"))
			{
				// Get a list of possible value of the attributes subfield
				List<String> attSubfieldValues = MarcXmlRecord.getSubfieldOfField(element, attributeToUse.getValue().charAt(1));

				// Use the first possible value if there were any
				if(attSubfieldValues.size() > 0)
					attributeToUse.setValue(attSubfieldValues.get(0));
			}

			for(String value : subfields)
			{
				// Setup the attribute list for the XC record field
				ArrayList<Attribute> attributes = new ArrayList<Attribute>();
				if(attributeToUse != null)
					attributes.add(attributeToUse);

				if(LOG.isDebugEnabled())
					LOG.debug("Adding a " + level + " level " + elementName + (attributeToUse == null ? "" : " with a " + attributeToUse.getName() + " of \"" + attributeToUse.getValue() + "\"") + " based on the " + field + " $" + subfield + " value, which is " + value);

				// Add the element to the XC record
				getXCRecordService().addElement(transformInto, elementName, value.trim(), elementNamespace, attributes, level);
			}
		}

		// Return the result
		return transformInto;
	}

	/**
	 * Process a data field from the MarcXmlRecord we're transforming and create the specified
	 * XC record field based on the datafield's value.  A datafield for which the target indicator
	 * does not match the required value will not be processed.
	 *
	 * @param transformMe The MARC XML record we're transforming
	 * @param transformInto The XC record which will store the transformed version of the record
	 * @param field The tag for the datafield to process
     * @param targetSubfields The subfields to process
     * @param elementName The name of the XC element to create
     * @param elementNamespace The namespace of the element to create
     * @param elementAttribute The attribute to set on the element to create
     * @param targetIndicator The indicator to base the attribute's value on
     * @param indicatorRequiredValue The value the indicator must have for a field to be processed
     * @param fieldToAttribute A map from the field code to the desired value of the attribute
     * @param level The FRBR level of the element to create
	 * @return A reference to transformInto after this transformation step has been completed.
	 */
	@SuppressWarnings("unchecked")
	protected AggregateXCRecord processFieldReqIndicatorAttFromField(MarcXmlRecord transformMe, AggregateXCRecord transformInto, String field, String targetSubfields, String elementName, Namespace elementNamespace, String targetIndicator, String indicatorRequiredValue, HashMap<String, Attribute> fieldToAttribute, FrbrLevel level)
	{
		
		// Get the elements with the requested field
		List<Element> elements = transformMe.getDataFields(field);

		// If there were no matching elements, return the unmodified XC record
		if(elements.size() == 0)
			return transformInto;

		
			// Add each subfield to the specified level with the specified tag and attribute
			for(Element element : elements)
			{
				// If they specified a required indicator, check that it exists and has the correct value
				if(targetIndicator != null && targetIndicator.length() > 0)
				{
					// Get the required indicator
					String ind = MarcXmlRecord.getIndicatorOfField(element, targetIndicator);
	
					// If the required indicator did not match the required value, continue to the next element without processing the current one
					if(ind == null || !ind.equals(indicatorRequiredValue))
						continue;
				}
	
				// Setup the attribute list for the processed field
				ArrayList<Attribute> attributes = new ArrayList<Attribute>();
	
				// A StringBuilder to concat the values of all the subfields of the Element
				StringBuilder builder = new StringBuilder();
	
				// Get the subfields of the current element
				List<Element> subfields = element.getChildren("subfield", marcNamespace);
	
				// Iterate over the subfields, and append each one to the StringBuilder if it
				// is in the list of target subfields
				for(Element subfield : subfields)
				{
					// Get the subfield's code
					String subfieldCode = subfield.getAttribute("code").getValue();
	
					if(targetSubfields.contains(subfieldCode))
						builder.append(subfield.getText() + " ");
	
					try{
					// If the subfield maps to an Attribute, add it to the attribute list
					if(fieldToAttribute.containsKey(subfieldCode))
						attributes.add(((Attribute)fieldToAttribute.get(subfieldCode).clone()).setValue(subfield.getText()));
					}
					catch (Exception e) {
						//TODO
						//errors.add(service.getId() + "-101: Subfield "+ subfieldCode +" is not repeatable in tag "+field);
					}
				}
	
				// If any target fields were found
				if(builder.length() > 0)
				{
					String value = builder.substring(0, builder.length()-1); // The value is everything except the last space
	
					if(LOG.isDebugEnabled())
						LOG.debug("Adding a " + level + " level " + elementName + " based on the concatination of the " + field + "'s subfields' value, which is " + value);
	
					// Add the element to the XC record
					getXCRecordService().addElement(transformInto, elementName, value.trim(), elementNamespace, attributes, level);
				}
			}
	
		
		// Return the result
		return transformInto;
	}

	/**
	 * Given a tag and its $0 value, return an authority Attribute for the field mapped from the tag
	 *
	 * @param tag The tag we're mapping
	 * @param valueOf0 The value of the tag's $0
	 * @return
	 */
	protected Attribute getAttributeForAuthority(String tag, String valueOf0)
	{
		// Check that the $0 is in the format we're expecting: "(<prefix>)<value>"
		// If it isn't, we can't build an authority Attribute, so return null
		if(!valueOf0.contains("(") || !valueOf0.contains(")"))
		{
			LOG.warn("Encountered a $0 subfield in a format which did not contain a prefix, so we can't build an authortiy Attribute for it.  Ignoring the malformed $0, which had a value of " + valueOf0);

			return null;
		}

		// Get the value and prefix of the control number
		String value = valueOf0.substring(valueOf0.indexOf(')') + 1);
		String prefix = valueOf0.substring(valueOf0.indexOf('(')+1, valueOf0.indexOf(')'));

		if(tag.equals("100") || tag.equals("110") || tag.equals("111"))
		{
			// If the prefix is "DLC" we should return an lcnaf attribute.
			// If it's the organization code we should return an xcauth attribute
			// Otherwise return null since we don't recognize the prefix
			if(prefix.equals("DLC"))
				return new Attribute("agentID", "lcnaf:n" + value);
			else if(prefix.equals(getOrganizationCode()))
				return new Attribute("agentID", "xcauth:" + value);
			else
				return null;
		}
		else if(tag.equals("440") || tag.equals("730") || tag.equals("800") || tag.equals("810") || tag.equals("811") || tag.equals("830"))
		{
			// If the prefix is "DLC" we should return an lcnaf attribute.
			// If it's the organization code we should return an xcauth attribute
			// Otherwise return null since we don't recognize the prefix
			if(prefix.equals("DLC"))
				return new Attribute("workID", "lcnaf:n" + value);
			else if(prefix.equals(getOrganizationCode()))
				return new Attribute("workID", "xcauth:" + value);
			else
				return null;
		}
		else if(tag.equals("600") || tag.equals("610") || tag.equals("611") || tag.equals("630") || tag.equals("650") || tag.equals("655") || tag.equals("965") || tag.equals("969"))
		{
			// If the prefix is "DLC" we should return an lcnaf attribute.
			// If it's the organization code we should return an xcauth attribute
			// Otherwise return null since we don't recognize the prefix
			if(prefix.equals("DLC"))
				return new Attribute("subjID", "lcnaf:sh" + value);
			else if(prefix.equals(getOrganizationCode()))
				return new Attribute("subjID", "xcauth:" + value);
			else
				return null;
		}
		else if(tag.equals("648") || tag.equals("963"))
		{
			// If the prefix is "DLC" we should return an lcnaf attribute.
			// If it's the organization code we should return an xcauth attribute
			// Otherwise return null since we don't recognize the prefix
			if(prefix.equals("DLC"))
				return new Attribute("chronID", "lcnaf:sh" + value);
			else if(prefix.equals(getOrganizationCode()))
				return new Attribute("chronID", "xcauth:" + value);
			else
				return null;
		}
		else if(tag.equals("651") || tag.equals("967"))
		{
			// If the prefix is "DLC" we should return an lcnaf attribute.
			// If it's the organization code we should return an xcauth attribute
			// Otherwise return null since we don't recognize the prefix
			if(prefix.equals("DLC"))
				return new Attribute("geoID", "lcnaf:sh" + value);
			else if(prefix.equals(getOrganizationCode()))
				return new Attribute("geoID", "xcauth:" + value);
			else
				return null;
		}

		// If we got here and didn't return anything, there is no Attribute defined for the presented tag and value
		return null;
	}

	/**
	 * Given a tag and its $0 value, return an authority Attribute for the field mapped from the tag. This
	 * method is intended to be used by 7XX fields with a 2nd indicator that is not 2
	 *
	 * @param tag The tag we're mapping
	 * @param valueOf0 The value of the tag's $0
	 * @param tPresent true iff the field contained a $t
	 * @return
	 */
	protected Attribute getAttributeForAuthority(String tag, String valueOf0, boolean tPresent)
	{
		// Check that the $0 is in the format we're expecting: "(<prefix>)<value>"
		// If it isn't, we can't build an authority Attribute, so return null
		if(!valueOf0.contains("(") || !valueOf0.contains(")"))
		{
			LOG.warn("Encountered a $0 subfield in a format which did not contain a prefix, so we can't build an authortiy Attribute for it.  Ignoring the malformed $0, which had a value of " + valueOf0);

			return null;
		}

		// Get the value and prefix of the control number
		String value = valueOf0.substring(valueOf0.indexOf(')') + 1);
		String prefix = valueOf0.substring(valueOf0.indexOf('(')+1, valueOf0.indexOf(')'));

		if(tag.equals("700") || tag.equals("710") || tag.equals("711"))
		{
			if(!tPresent)
			{
				// If the prefix is "DLC" we should return an lcnaf attribute.
				// If it's the organization code we should return an xcauth attribute
				// Otherwise return null since we don't recognize the prefix
				if(prefix.equals("DLC"))
					return new Attribute("agentID", "lcnaf:" + value);
				else if(prefix.equals(getOrganizationCode()))
					return new Attribute("agentID", "xcauth:" + value);
				else
					return null;
			}
			else
			{
				// If the prefix is "DLC" we should return an lcnaf attribute.
				// If it's the organization code we should return an xcauth attribute
				// Otherwise return null since we don't recognize the prefix
				if(prefix.equals("DLC"))
					return new Attribute("workID", "lcnaf:n" + value);
				else if(prefix.equals(getOrganizationCode()))
					return new Attribute("workID", "xcauth:" + value);
				else
					return null;
			}
		}

		// If we got here and didn't return anything, there is no Attribute defined for the presented tag and value
		return null;
	}

	/**
	 * Given a tag and its $0 value, return an authority Attribute for the field mapped from the tag
	 *
	 * @param transformMe The MARCXML record we're transforming
	 * @param transformInto The XC record from the transformation we've preformed so far
	 * @param tag The tag we're mapping
	 * @param level The FRBR level to add the result to
	 * @return
	 */
	protected AggregateXCRecord addElementForAuthority(MarcXmlRecord transformMe, AggregateXCRecord transformInto, String tag, String valueOf0, String linkingField)
	{
		// Check that the $0 is in the format we're expecting: "(<prefix>)<value>"
		// If it isn't, we can't build an authority Attribute, so return null
		if(!valueOf0.contains("(") || !valueOf0.contains(")"))
		{
			LOG.warn("Encountered a $0 subfield in a format which did not contain a prefix, so we can't build an authortiy Attribute for it.  Ignoring the malformed $0, which had a value of " + valueOf0);

			return transformInto;
		}

		// Get the value and prefix of the control number
		String value = valueOf0.substring(valueOf0.indexOf(')') + 1);
		String prefix = valueOf0.substring(valueOf0.indexOf('(')+1, valueOf0.indexOf(')'));

		if(tag.equals("700") || tag.equals("710") || tag.equals("711") || tag.equals("730"))
		{
			// If the prefix is "DLC" we should return an lcnaf attribute.
			// If it's the organization code we should return an xcauth attribute
			// Otherwise return null since we don't recognize the prefix
			if(prefix.equals("DLC"))
			{
				ArrayList<Attribute> atts = new ArrayList<Attribute>();
				atts.add(new Attribute("type", "lcnaf", AggregateXCRecord.XSI_NAMESPACE));
				getXCRecordService().addElementBasedOnLinkingField(transformInto, "identifierForTheWork", "n" + value, AggregateXCRecord.RDVOCAB_NAMESPACE, atts, linkingField);
			}
			else if(prefix.equals(getOrganizationCode()))
			{
				ArrayList<Attribute> atts = new ArrayList<Attribute>();
				atts.add(new Attribute("type", "xcauth"));
				getXCRecordService().addElementBasedOnLinkingField(transformInto, "identifierForTheWork", value, AggregateXCRecord.RDVOCAB_NAMESPACE, atts, linkingField);
			}
		}

		// If we got here and didn't return anything, there is no Attribute defined for the presented tag and value
		return transformInto;
	}
	
	@Override
	public void loadConfiguration(String config){	
		
	}
	
	@Override
	protected void validateService() throws ServiceValidationException {
		
	}

	protected String getOrganizationCode() {

		return orgCode;
	}
	
	public void setInputRecordCount(int inputRecordCount) {
		this.inputRecordCount = inputRecordCount;
	}
}
