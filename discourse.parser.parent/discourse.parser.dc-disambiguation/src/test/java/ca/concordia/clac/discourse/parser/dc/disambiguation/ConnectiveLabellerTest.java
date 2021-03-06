package ca.concordia.clac.discourse.parser.dc.disambiguation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.BiConsumer;

import org.apache.uima.UIMAException;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.cleartk.discourse.type.DiscourseConnective;
import org.junit.Test;

import ca.concordia.clac.discourse.parser.dc.disambiguation.DiscourseVsNonDiscourseClassifier;

public class ConnectiveLabellerTest {

	@Test
	public void whenTheLabelIsTrueThenConnectiveIsAddedToTheJCas() throws UIMAException{
		JCas aJCas = JCasFactory.createJCas();
		
		aJCas.setDocumentText("but, this is another issue.");
		DiscourseConnective connective = new DiscourseConnective(aJCas, 0, "but".length());
		
		BiConsumer<String, DiscourseConnective> labeller = new DiscourseVsNonDiscourseClassifier().getLabeller(aJCas);
		labeller.accept("true", connective);
		
		assertThat(JCasUtil.select(aJCas, DiscourseConnective.class)).hasSize(1);
	}
	
	@Test
	public void whenTheLabelIsFalseThenConnectiveIsNotAddedToTheJCas() throws UIMAException{
		JCas aJCas = JCasFactory.createJCas();
		
		aJCas.setDocumentText("but, this is another issue.");
		DiscourseConnective connective = new DiscourseConnective(aJCas, 0, "but".length());
		
		BiConsumer<String, DiscourseConnective> labeller = new DiscourseVsNonDiscourseClassifier().getLabeller(aJCas);
		labeller.accept("false", connective);
		
		assertThat(JCasUtil.select(aJCas, DiscourseConnective.class)).hasSize(0);
	}
}
