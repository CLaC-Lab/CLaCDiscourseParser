package ca.concordia.clac.uima.engines;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASRuntimeException;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Text;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

public class XMLGenerator extends JCasAnnotator_ImplBase{
	public static final String PARAM_OUTPUT_DIRECTORY = "outputFolder";
	public static final String PARAM_CREATE_SHORT_NAME = "createShortName";
	public static final String PARAM_OUTPUT_TYPES = "outputTypes";
	public static final String PARAM_OUTPUT_FILE_SUFFIX = "postfix";
	public static final String PARAM_SUPER_INDEXED_ANNOTATION_NAME = "superIndexedAnnotationName";
	public static final String PARAM_INDEXED_ANNOTATION_NAME = "indexedAnnotationName";

	public static final String ROOT_ELEMENTE = "DOCUMENT";

	@ConfigurationParameter(name=PARAM_OUTPUT_DIRECTORY, mandatory = false)
	private File outputFolder;

	@ConfigurationParameter(name=PARAM_CREATE_SHORT_NAME)
	private boolean createShortName;

	@ConfigurationParameter(name=PARAM_OUTPUT_TYPES)
	private String[] outputTypes;

	private List<String> outputTypesInOrder;
	private Set<String> validTypes;

	@ConfigurationParameter(name=PARAM_OUTPUT_FILE_SUFFIX)
	private String postfix;

	@ConfigurationParameter(name=PARAM_SUPER_INDEXED_ANNOTATION_NAME, mandatory = false)
	private String superIndexedAnnotationName;
	@ConfigurationParameter(name=PARAM_INDEXED_ANNOTATION_NAME, mandatory = false)
	private String indexedAnnotationName;


	private Class<? extends Annotation> superIndexedAnnotation;
	private Class<? extends Annotation> indexedAnnotation;

	private DocumentBuilder build;
	private Transformer trans;

	private Map<Integer, Integer> indexes = new HashMap<>();
	private Map<Integer, List<Annotation>> startIndex = new HashMap<>();
	private Map<Integer, List<Annotation>> endIndex  = new HashMap<>();
	private Map<FeatureStructure, Integer> annotationToIdx = new HashMap<>();
	private int fileIdx = 0;

	/**
	 * 
	 * @param outputFolder: an output folder or null if the output is the terminal.
	 * @param suffix: suffix for the files if the inputs are a set of file in the outputFolder
	 * @param superIndexedAnnationClass
	 * @param indexedAnnationClass
	 * @param createShortName
	 * @param types
	 * @return
	 * @throws ResourceInitializationException
	 */
	public static AnalysisEngineDescription getDescription(File outputFolder, String suffix, Class<? extends Annotation> superIndexedAnnationClass, 
			Class<? extends Annotation> indexedAnnationClass, boolean createShortName, String... types) throws ResourceInitializationException {

		String indexedAnnotaitonName = indexedAnnationClass != null ? indexedAnnationClass.getName() : null;
		String superIndexedAnnotaitonName = superIndexedAnnationClass != null ? superIndexedAnnationClass.getName() : null;
		return AnalysisEngineFactory.createEngineDescription(XMLGenerator.class, 
				PARAM_OUTPUT_DIRECTORY, outputFolder == null ? null : outputFolder.getAbsolutePath(), 
						PARAM_CREATE_SHORT_NAME, createShortName, 
						PARAM_OUTPUT_FILE_SUFFIX, suffix,
						PARAM_SUPER_INDEXED_ANNOTATION_NAME, superIndexedAnnotaitonName,
						PARAM_INDEXED_ANNOTATION_NAME, indexedAnnotaitonName,
						PARAM_OUTPUT_TYPES, types);

	}

	public static AnalysisEngineDescription getDescription(File outputFolder, String postfix, boolean createShortName, String... types) throws ResourceInitializationException {
		return getDescription(outputFolder, postfix, null, null, createShortName, types);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void initialize(UimaContext context) throws ResourceInitializationException {
		super.initialize(context);

		validTypes = new HashSet<>(Arrays.asList(outputTypes));
		outputTypesInOrder = Arrays.asList(outputTypes);
		DocumentBuilderFactory dFact = DocumentBuilderFactory.newInstance();
		try {
			build = dFact.newDocumentBuilder();
			TransformerFactory tFact = TransformerFactory.newInstance();
			trans = tFact.newTransformer();

			if (outputFolder != null)
				outputFolder.mkdir();

			if (superIndexedAnnotationName != null)
				superIndexedAnnotation = (Class<? extends Annotation>) Class.forName(superIndexedAnnotationName);
			if (indexedAnnotationName != null)
				indexedAnnotation =  (Class<? extends Annotation>) Class.forName(indexedAnnotationName);

		} catch (ParserConfigurationException | TransformerConfigurationException | ClassNotFoundException e) {
			throw new ResourceInitializationException(e);
		}
	}

	private String convertToString(Annotation ann){
		if (ann == null)
			return "null";
		return createShortName ? ann.getType().getShortName() : ann.getType().getName();

	}
	
	private static boolean isEqual(Annotation a, Annotation b){
		if (a.getClass().equals(b.getClass()) && a.getBegin() == b.getBegin() && a.getEnd() == b.getEnd())
			return true;
		return false;
	}

	@Override
	public void process(JCas aJCas) throws AnalysisEngineProcessException {
		PrintWriter output;
		if (outputFolder != null){
			try {
				output = new PrintWriter(new OutputStreamWriter(new FileOutputStream(new File(outputFolder, getFileName(aJCas))), StandardCharsets.UTF_8));
			} catch (FileNotFoundException e) {
				throw new AnalysisEngineProcessException(e);
			}
		} 
		else {
			output = new PrintWriter(System.out);
		}

		Document doc = build.newDocument();
		Element root = doc.createElement(ROOT_ELEMENTE);
		doc.appendChild(root);
		buildAnnotationsIndexe(aJCas);
		setIndexes(aJCas);

		String docText = aJCas.getDocumentText();
		LinkedList<Element> elementStack = new LinkedList<>();
		LinkedList<Annotation> annotationStack = new LinkedList<>();
		
		elementStack.push(root);annotationStack.push(null);
		
		Set<Annotation> invalidAnnotaitons = new HashSet<>();

		int prev = 0;
		for (int i = 0; i < docText.length(); i++){
			//get annotations that start or end at the current positions.
			List<Annotation> startAnnotaitons = startIndex.get(i);
			List<Annotation> endAnnotaitons = endIndex.get(i);

			if (startAnnotaitons != null || endAnnotaitons != null){	//if there are annotations that are opened/closed here we need to change the xml structure to add/close nodes.
				if (prev != i){	//lets first put all the text into xml
					Text textNode = doc.createTextNode(getText(docText, prev, i));
					prev = i;
					elementStack.peekFirst().appendChild(textNode);
				}

				if (endAnnotaitons != null)	//we need to process annotations that are ended here first before opening a new annotation here.
					for (Annotation ann: endAnnotaitons) {
						if (invalidAnnotaitons.remove(ann))
							continue;
						if (ann.getBegin() == ann.getEnd())
							continue;
						if (annotationStack.peekFirst() != null && !isEqual(annotationStack.peekFirst(), ann)){
							saveXML(output, doc);
							throw new AnalysisEngineProcessException(
									new RuntimeException(String.format("Cannot create an xml from the document, expecting to close %s = `%s`, but %s = `%s` is closed. "
											+ "Stack content:\n%s", 
											convertToString(annotationStack.peekFirst()), annotationStack.peekFirst().getCoveredText(), 
											convertToString(ann), ann.getCoveredText(), 
											annotationStack.stream().map(a -> convertToString(a)).collect(Collectors.joining("\n"))
													)));
						}
						elementStack.pop();
						annotationStack.pop();
					}

				if (startAnnotaitons != null)
					for (Annotation ann: startAnnotaitons){
						if (ann.getBegin() != ann.getEnd()){	//annotations with zero length will be ignored
							if (annotationStack.peekFirst() != null && ann.getEnd() > annotationStack.peekFirst().getEnd()){	//this annotation is an invalid annotation
								System.err.println(String.format("XMLGenerator.process(): This annotation is an invalid anntation as it does not fit to its parent:"
										+ "Parent = %s:\n%s\nChild = %s:%s", 
										convertToString(annotationStack.peekFirst()), 
										annotationStack.peekFirst().getCoveredText(),
										convertToString(ann), 
										ann.getCoveredText()));
								invalidAnnotaitons.add(ann);
							} else {
								Element child = createAnElement(doc, ann);
								elementStack.peekFirst().appendChild(child);
								elementStack.push(child);
								annotationStack.push(ann);
							}
						}

					}
			}	
		}

		Text textNode = doc.createTextNode(getText(docText, prev, docText.length()));

		elementStack.pop().appendChild(textNode);

		saveXML(output, doc);
	}

	private void setIndexes(JCas aJCas) {
		if (superIndexedAnnotation == null || indexedAnnotation == null)
			return;

		indexes.clear();
		Map<String, Integer> annToEnd = new HashMap<>();
		Map<String, Integer> annCnt = new HashMap<>();

		Map<? extends Annotation, ?> indexCovered = JCasUtil.indexCovered(aJCas, superIndexedAnnotation, indexedAnnotation);
		for (Entry<? extends Annotation, ?> anEntry: indexCovered.entrySet()){
			Collection<?> targetAnnotations = (Collection<?>) anEntry.getValue();

			annToEnd.clear();
			annCnt.clear();
			for (Object obj: targetAnnotations){
				Annotation ann = (Annotation) obj;
				String text = ann.getCoveredText().toLowerCase();
				if (text.length() == 1 && !Character.isLetter(text.charAt(0)))
					continue;
				Integer prevEnd = annToEnd.put(text, ann.getEnd());
				if (prevEnd != null){ //there is duplicate value for the annotation, lets put indexes
					Integer cnt = annCnt.get(text);
					if (cnt == null){	//this is the first time that we found duplicate annotations, let add index for the first one too
						indexes.put(prevEnd - 1, 1);
						cnt = 1;
					}
					annCnt.put(text, cnt + 1);
					annToEnd.put(text, ann.getEnd());
					indexes.put(ann.getEnd() - 1, cnt + 1);
				}
			}
		}

	}

	private String getText(String docText, int start, int end) {
		StringBuilder sb = new StringBuilder();
		for (int i = start; i < end; i++){
			sb.append(docText.charAt(i));
			if (indexes.get(i) != null){
				sb.append("<sub>");
				sb.append(indexes.get(i).toString());
				sb.append("</sub>");
			}
		}

		return sb.toString();
	}

	private void saveXML(PrintWriter output, Document doc) throws AnalysisEngineProcessException {
		StreamResult result = new StreamResult(output);
		DOMSource source = new DOMSource(doc);
		try {
			trans.transform(source, result);

		} catch (TransformerException e) {
			throw new AnalysisEngineProcessException(e);
		} finally {
			output.close();
		}
	}

	private String getFileName(JCas aJCas) throws AnalysisEngineProcessException {
		String docName;
		try {
			docName = Tools.getDocName(aJCas);
			int extensionStart = docName.lastIndexOf('.');
			if (extensionStart == -1)
				extensionStart = docName.length();
			docName = docName.substring(docName.lastIndexOf('/') + 1, extensionStart);
			return docName + postfix;
		} catch (IllegalArgumentException e) {
			fileIdx++;
			return "" + fileIdx + postfix;
		}
	}

	private Element createAnElement(Document doc, Annotation ann) {
		Element anElement = doc.createElement(convertToString(ann));

		anElement.setAttribute("annotation_id", getId(ann));
		for (Feature feature: ann.getType().getFeatures()){
			switch (feature.getShortName()) {
			case "begin":
			case "end":
			case "classLabel":
			case "sofa":
				break;
			default:
				String value = convertToString(ann, feature);
				if (value != null)
					anElement.setAttribute(feature.getShortName(), value);
				break;
			}
		}
		return anElement;
	}

	private String convertToString(Annotation toBePrinted, Feature feature) {
		if (feature.getRange().isArray()){
			FSArray array = (FSArray) toBePrinted.getFeatureValue(feature);
			List<String> res = new ArrayList<>();
			for (int i = 0; i < array.size(); i++){
				res.add(getId(array.get(i)));
			}
			return res.toString();
		}
		return safeToString(toBePrinted, feature);
	}

	private String safeToString(FeatureStructure toBePrinted, Feature feature){
		try {
			return toBePrinted.getFeatureValueAsString(feature);
		} catch (CASRuntimeException e) {
			FeatureStructure featureValue = toBePrinted.getFeatureValue(feature);
			if (featureValue != null){
				return featureValue.getType().getShortName() + "-" + getId(featureValue);
			}
			return null;
		}
	}

	private String getId(FeatureStructure featureValue) {
		Integer id = annotationToIdx.get(featureValue);
		if (id == null && featureValue instanceof Annotation){
			id = ((Annotation)featureValue).getBegin();
		}
		return id == null ? null : id.toString();
	}

	private void buildAnnotationsIndexe(JCas aJCas) {
		Collection<Annotation> annotations = JCasUtil.select(aJCas, Annotation.class);
		annotations = filter(annotations); 
		ListMultimap<Integer, Annotation> tempStartIndex = ArrayListMultimap.create();
		ListMultimap<Integer, Annotation> tempEndIndex = ArrayListMultimap.create();

		for (Annotation annotation: annotations){
			tempStartIndex.put(annotation.getBegin(), annotation);
			tempEndIndex.put(annotation.getEnd(), annotation);
			annotationToIdx.put(annotation, annotation.getBegin());
		}

		Comparator<? super Annotation> comparatorEnd = (a, b) -> {
			int endComparison = new Integer(a.getEnd()).compareTo(b.getEnd());
			if (endComparison != 0)
				return -endComparison;
			return compareTwoAnnotation(a, b);
		};
		createAnIndex(tempStartIndex, startIndex, comparatorEnd);

		Comparator<? super Annotation> comparatorStart = (a, b) -> {
			int beginComparison = new Integer(a.getBegin()).compareTo(b.getBegin());
			if (beginComparison != 0)
				return -beginComparison;
			return -compareTwoAnnotation(a, b);
		};
		createAnIndex(tempEndIndex, endIndex, comparatorStart);
	}

	public int compareTwoAnnotation(Annotation a, Annotation b){
		int idxa = outputTypesInOrder.indexOf(convertToString(a));
		int idxb = outputTypesInOrder.indexOf(convertToString(b));

		if (idxa == -1 || idxb == -1){
			return convertToString(a).compareTo(convertToString(b));
		} 

		if (idxa < idxb)
			return -1;

		if (idxa > idxb)
			return +1;
		return 0;
	}

	private Collection<Annotation> filter(Collection<Annotation> annotations) {
		if (outputTypes.length == 0)
			return annotations;

		List<Annotation> filtered = new ArrayList<>();
		for (Annotation ann: annotations){
			if (validTypes.contains(convertToString(ann))){
				filtered.add(ann);
			}
		}

		return filtered;
	}

	private void createAnIndex(ListMultimap<Integer, Annotation> tempStartIndex, Map<Integer, List<Annotation>> index,
			Comparator<? super Annotation> comparator) {
		index.clear();
		Map<Integer, Collection<Annotation>> asMap = tempStartIndex.asMap();
		for (Entry<Integer, Collection<Annotation>> anEntry: asMap.entrySet()){
			List<Annotation> annotationsAtPos = new ArrayList<>(anEntry.getValue());
			Collections.sort(annotationsAtPos, comparator);
			index.put(anEntry.getKey(), annotationsAtPos);
		}
	}

}
