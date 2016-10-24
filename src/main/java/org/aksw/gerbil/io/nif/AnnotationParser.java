/**
 * This file is part of NIF transfer library for the General Entity Annotator
 * Benchmark.
 *
 * NIF transfer library for the General Entity Annotator Benchmark is free
 * software: you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * NIF transfer library for the General Entity Annotator Benchmark is
 * distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with NIF transfer library for the General Entity Annotator Benchmark.
 * If not, see <http://www.gnu.org/licenses/>.
 */
package org.aksw.gerbil.io.nif;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.aksw.gerbil.transfer.nif.Document;
import org.aksw.gerbil.transfer.nif.Marking;
import org.aksw.gerbil.transfer.nif.MeaningSpan;
import org.aksw.gerbil.transfer.nif.data.Annotation;
import org.aksw.gerbil.transfer.nif.data.NamedEntity;
import org.aksw.gerbil.transfer.nif.data.ScoredAnnotation;
import org.aksw.gerbil.transfer.nif.data.ScoredNamedEntity;
import org.aksw.gerbil.transfer.nif.data.ScoredTypedNamedEntity;
import org.aksw.gerbil.transfer.nif.data.SpanImpl;
import org.aksw.gerbil.transfer.nif.data.TypedNamedEntity;
import org.aksw.gerbil.transfer.nif.data.TypedSpanImpl;
import org.aksw.gerbil.transfer.nif.vocabulary.ITSRDF;
import org.aksw.gerbil.transfer.nif.vocabulary.NIF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.NodeIterator;
import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.vocabulary.RDF;

public class AnnotationParser {

	private static final Logger LOGGER = LoggerFactory.getLogger(AnnotationParser.class);

	private boolean removeUsedProperties;

	public AnnotationParser() {
		this(false);
	}

	public AnnotationParser(final boolean removeUsedProperties) {
		this.removeUsedProperties = removeUsedProperties;
	}

	public void parseAnnotations(final Model nifModel, final Document document, final Resource documentResource) {
		// get the annotations from the model

		List<Marking> markings = document.getMarkings();
		ResIterator resIter = nifModel.listSubjectsWithProperty(NIF.referenceContext, documentResource);
		Resource annotationResource;
		int start, end;
		Set<String> entityUris;
		double confidence;
		NodeIterator nodeIter;
		while (resIter.hasNext()) {

			annotationResource = resIter.next();

			start = end = -1;
			nodeIter = nifModel.listObjectsOfProperty(annotationResource, NIF.beginIndex);
			if (nodeIter.hasNext()) {
				start = nodeIter.next().asLiteral().getInt();
			}
			nodeIter = nifModel.listObjectsOfProperty(annotationResource, NIF.endIndex);
			if (nodeIter.hasNext()) {
				end = nodeIter.next().asLiteral().getInt();
			}
			if ((start >= 0) && (end >= 0)) {
				boolean isWord = nifModel.contains(annotationResource, RDF.type, NIF.Word);

				nodeIter = nifModel.listObjectsOfProperty(annotationResource, ITSRDF.taIdentRef);
				if (nodeIter.hasNext()) {
					entityUris = new HashSet<>();
					while (nodeIter.hasNext()) {
						entityUris.add(nodeIter.next().toString());
					}
					nodeIter = nifModel.listObjectsOfProperty(annotationResource, ITSRDF.taClassRef);
					if (nodeIter.hasNext()) {
						Set<String> types = new HashSet<>();
						while (nodeIter.hasNext()) {
							types.add(nodeIter.next().toString());
						}
						nodeIter = nifModel.listObjectsOfProperty(annotationResource, ITSRDF.taConfidence);
						if (nodeIter.hasNext()) {
							confidence = nodeIter.next().asLiteral().getDouble();
							markings.add(addTypeInformation(new ScoredTypedNamedEntity(start, end - start, entityUris, types, confidence, isWord), nifModel));
						} else {
							// It has been typed without a confidence
							markings.add(addTypeInformation(new TypedNamedEntity(start, end - start, entityUris, types, isWord), nifModel));
						}
					} else {
						nodeIter = nifModel.listObjectsOfProperty(annotationResource, ITSRDF.taConfidence);
						if (nodeIter.hasNext()) {
							confidence = nodeIter.next().asLiteral().getDouble();
							markings.add(addTypeInformationIfPossible(new ScoredNamedEntity(start, end - start, entityUris, confidence, isWord), nifModel));
						} else {
							// It has been disambiguated without a confidence
							markings.add(addTypeInformationIfPossible(new NamedEntity(start, end - start, entityUris, isWord), nifModel));
						}
					}
				} else {
					nodeIter = nifModel.listObjectsOfProperty(annotationResource, ITSRDF.taClassRef);
					if (nodeIter.hasNext()) {
						Set<String> types = new HashSet<>();
						while (nodeIter.hasNext()) {
							types.add(nodeIter.next().toString());
						}
						// It has been typed without a confidence
						markings.add(new TypedSpanImpl(start, end - start, types, isWord));
					} else {
						// It is a named entity that hasn't been disambiguated
						markings.add(new SpanImpl(start, end - start, isWord));

					}
				}
				// FIXME scored Span is missing
			} else {
				LOGGER.warn("Found an annotation resource (\"" + annotationResource.getURI() + "\") without a start or end index. This annotation will be ignored.");
			}
			if (removeUsedProperties) {
				nifModel.removeAll(annotationResource, null, null);
			}
		}

		NodeIterator annotationIter = nifModel.listObjectsOfProperty(documentResource, NIF.topic);
		while (annotationIter.hasNext()) {
			annotationResource = annotationIter.next().asResource();
			nodeIter = nifModel.listObjectsOfProperty(annotationResource, ITSRDF.taIdentRef);
			if (nodeIter.hasNext()) {
				entityUris = new HashSet<>();
				while (nodeIter.hasNext()) {
					entityUris.add(nodeIter.next().toString());
				}
				nodeIter = nifModel.listObjectsOfProperty(annotationResource, ITSRDF.taConfidence);
				if (nodeIter.hasNext()) {
					confidence = nodeIter.next().asLiteral().getDouble();
					markings.add(new ScoredAnnotation(entityUris, confidence));
				} else {
					markings.add(new Annotation(entityUris));
				}
			}
		}
	}

	private MeaningSpan addTypeInformationIfPossible(final NamedEntity ne, final Model nifModel) {
		TypedNamedEntity typedNE = new TypedNamedEntity(ne.getStartPosition(), ne.getLength(), ne.getUris(), new HashSet<String>(), ne.getIsWord());
		addTypeInformation(typedNE, nifModel);
		if (typedNE.getTypes().size() > 0) {
			return typedNE;
		} else {
			return ne;
		}
	}

	private MeaningSpan addTypeInformationIfPossible(final ScoredNamedEntity ne, final Model nifModel) {
		ScoredTypedNamedEntity typedNE = new ScoredTypedNamedEntity(ne.getStartPosition(), ne.getLength(), ne.getUris(), new HashSet<String>(), ne.getConfidence(), ne.getIsWord());
		addTypeInformation(typedNE, nifModel);
		if (typedNE.getTypes().size() > 0) {
			return typedNE;
		} else {
			return ne;
		}
	}

	private TypedNamedEntity addTypeInformation(final TypedNamedEntity typedNE, final Model nifModel) {
		for (String uri : typedNE.getUris()) {
			NodeIterator nodeIter = nifModel.listObjectsOfProperty(nifModel.getResource(uri), RDF.type);
			Set<String> types = typedNE.getTypes();
			while (nodeIter.hasNext()) {
				types.add(nodeIter.next().asResource().getURI());
			}
		}
		return typedNE;
	}

}
