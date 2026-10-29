import { DataFactory, type Literal, type Term } from "n3";
import { RDF, RDFS_LABEL } from "./rdf";
import type { ModelData } from "./types";

export interface AttributeValue { text: string; iri?: string }
export interface AttributeRow { name: string; iri: string; values: AttributeValue[] }

const XSD_STRING = "http://www.w3.org/2001/XMLSchema#string";

export function compactIri(iri: string, prefixes: Record<string, string>) {
  const match = Object.entries(prefixes).filter(([, namespace]) => iri.startsWith(namespace)).sort((a, b) => b[1].length - a[1].length)[0];
  return match ? `${match[0]}:${iri.slice(match[1].length)}` : iri;
}

export function lbdAttributes(data: ModelData, id: string): AttributeRow[] {
  const subject = DataFactory.namedNode(id);
  const grouped = new Map<string, Term[]>();
  for (const quad of data.store.getQuads(subject, null, null, null)) (grouped.get(quad.predicate.value) ?? grouped.set(quad.predicate.value, []).get(quad.predicate.value)!).push(quad.object);
  return [...grouped].map(([iri, terms]) => ({
    name: iri === RDF ? "type" : iri === RDFS_LABEL ? "label" : compactIri(iri, data.prefixes),
    iri,
    values: terms.map(term => attributeValue(data, term)),
  })).sort((a, b) => rank(a.iri) - rank(b.iri) || a.name.localeCompare(b.name));
}

function rank(iri: string) { return iri === RDFS_LABEL ? 0 : iri === RDF ? 1 : 2; }

function attributeValue(data: ModelData, term: Term): AttributeValue {
  if (term.termType === "Literal") return { text: literalText(term, data.prefixes) };
  if (term.termType === "BlankNode") return { text: `_:${term.value}` };
  const label = data.store.getQuads(term, DataFactory.namedNode(RDFS_LABEL), null, null)[0]?.object.value;
  const compact = compactIri(term.value, data.prefixes);
  return { text: label && label !== compact ? `${label} (${compact})` : compact, iri: term.value };
}

function literalText(literal: Literal, prefixes: Record<string, string>) {
  if (literal.language) return `${literal.value} @${literal.language}`;
  if (literal.datatype.value === XSD_STRING) return literal.value;
  return `${literal.value} · ${compactIri(literal.datatype.value, prefixes)}`;
}
