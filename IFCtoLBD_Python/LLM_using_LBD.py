#-------------------------------------------------------------------------------
# Name:        LLM Example 
# Author:      Jyrki Oraskari
# Functionality: Fetches the information about the building of the LBD model
# (OPT level 1) and creates a textual description of it.
# Created:     13/08/2025
# Copyright:   (c) Jyrki Oraskari 2025
# Licence:     MIT
#-------------------------------------------------------------------------------
"""
To install:
-  install Ollama
   This may be in the default installation, but just in case:
   From the command line: ollama pull llama3
Then:
pip install sparqlwrapper
pip install rdflib
pip install dspy
"""

from SPARQLWrapper import SPARQLWrapper, JSON
from rdflib import Graph
import json
import dspy

class GenerateAnswer(dspy.Signature):
    """Answer questions with logical factoid answers."""

    context = dspy.InputField(desc="will contain a BIN related document")
    question = dspy.InputField()
    answer = dspy.OutputField(desc="an answer within 20 to 30 words")

class GraphSparqlQAChain:
    def __init__(self, model_name: str, graph_file: str):
        self.model_name = model_name
        self.graph_file = graph_file

    @staticmethod
    def from_llm(model_name: str, graph_file: str):
        return GraphSparqlQAChain(model_name, graph_file)

    def query_graph(self):

        # Load your TTL file
        g = Graph()
        g.parse(self.graph_file, format="turtle")

        queryString = """PREFIX bot: <https://w3id.org/bot#>
        SELECT ?building ?predicate ?object
        WHERE {
        ?building a bot:Building .
        ?building ?predicate ?object
        }LIMIT 10

        """

        # Execute the query on the local graph
        results = g.query(queryString)

        # Convert the results to JSON
        results_json = []
        for row in results:
            result_dict = {str(var): str(row[var]) for var in row.labels}
            results_json.append(result_dict)
        return results_json

    def answer_question(self, question: str):
        results = self.query_graph()
        context_txt = " ".join([result["predicate"]+" "+result["object"]+". "
            for result in results])
   
        lm = dspy.LM("ollama_chat/deepseek-r1:8b",
            api_base="http://localhost:11434", api_key="")
        dspy.configure(lm=lm)

        generate_answer = dspy.ChainOfThought(GenerateAnswer)

        prediction = generate_answer(context=context_txt, question=question)
        response=dspy.Prediction(context=context_txt, answer=prediction.answer)
        return response.answer

# Example usage
graph_file = "Duplex_A_20110907_LBD.ttl"

chain = GraphSparqlQAChain.from_llm("llama3", graph_file)

# Get the answer from the chain
answer = chain.answer_question("Describe this as text.")

# Print the answer
print("The answer is:")
print(answer)
