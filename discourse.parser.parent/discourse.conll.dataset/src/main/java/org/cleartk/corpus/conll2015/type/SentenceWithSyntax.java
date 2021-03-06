

/* First created by JCasGen Tue Mar 24 16:40:12 EDT 2015 */
package org.cleartk.corpus.conll2015.type;

import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.JCasRegistry;
import org.apache.uima.jcas.cas.TOP_Type;

import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;


/** 
 * Updated by JCasGen Thu Nov 19 12:11:45 EST 2015
 * XML source: /Users/majid/Documents/workspace/clac-parser/clac.discourse.parser/conll.dataset/src/main/resources/org/cleartk/corpus/conll2015/TypeSystem.xml
 * @generated */
public class SentenceWithSyntax extends Sentence {
  /** @generated
   * @ordered 
   */
  public final static int typeIndexID = JCasRegistry.register(SentenceWithSyntax.class);
  /** @generated
   * @ordered 
   */
  public final static int type = typeIndexID;
  /** @generated
   * @return index of the type  
   */
  @Override
  public              int getTypeIndexID() {return typeIndexID;}
 
  /** Never called.  Disable default constructor
   * @generated */
  protected SentenceWithSyntax() {/* intentionally empty block */}
    
  /** Internal - constructor used by generator 
   * @generated
   * @param addr low level Feature Structure reference
   * @param type the type of this Feature Structure 
   */
  public SentenceWithSyntax(int addr, TOP_Type type) {
    super(addr, type);
    readObject();
  }
  
  /** @generated
   * @param jcas JCas to which this Feature Structure belongs 
   */
  public SentenceWithSyntax(JCas jcas) {
    super(jcas);
    readObject();   
  } 

  /** @generated
   * @param jcas JCas to which this Feature Structure belongs
   * @param begin offset to the begin spot in the SofA
   * @param end offset to the end spot in the SofA 
  */  
  public SentenceWithSyntax(JCas jcas, int begin, int end) {
    super(jcas);
    setBegin(begin);
    setEnd(end);
    readObject();
  }   

  /** 
   * <!-- begin-user-doc -->
   * Write your own initialization here
   * <!-- end-user-doc -->
   *
   * @generated modifiable 
   */
  private void readObject() {/*default - does nothing empty block */}
     
 
    
  //*--------------*
  //* Feature: syntaxTree

  /** getter for syntaxTree - gets 
   * @generated
   * @return value of the feature 
   */
  public String getSyntaxTree() {
    if (SentenceWithSyntax_Type.featOkTst && ((SentenceWithSyntax_Type)jcasType).casFeat_syntaxTree == null)
      jcasType.jcas.throwFeatMissing("syntaxTree", "org.cleartk.corpus.conll2015.type.SentenceWithSyntax");
    return jcasType.ll_cas.ll_getStringValue(addr, ((SentenceWithSyntax_Type)jcasType).casFeatCode_syntaxTree);}
    
  /** setter for syntaxTree - sets  
   * @generated
   * @param v value to set into the feature 
   */
  public void setSyntaxTree(String v) {
    if (SentenceWithSyntax_Type.featOkTst && ((SentenceWithSyntax_Type)jcasType).casFeat_syntaxTree == null)
      jcasType.jcas.throwFeatMissing("syntaxTree", "org.cleartk.corpus.conll2015.type.SentenceWithSyntax");
    jcasType.ll_cas.ll_setStringValue(addr, ((SentenceWithSyntax_Type)jcasType).casFeatCode_syntaxTree, v);}    
  }

    