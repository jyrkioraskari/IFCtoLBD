#-------------------------------------------------------------------------------
# Name:        LLM Example 4
# Author:      Jyrki Oraskari
#
# Created:     13/08/2025
# Copyright:   (c) Jyrki Oraskari 2025
# Licence:     MIT
#-------------------------------------------------------------------------------
#from transformers import pipeline
from SPARQLWrapper import SPARQLWrapper, JSON
from rdflib import Graph
import json
import dspy

"""
Author: Jyrki Oraskari
Functionality: Fetches the information about the building of the LBD model (OPT level 1) and creates a textual description of it.
Date: 20 February 2025
License: Apache 2

To install:
-  install Ollama

Then:
pip install sparqlwrapper
pip install rdflib
pip install dspy

"""



class BuildingInfoFetcher:
    def __init__(self):
        # Initialize your fetcher here
        pass

    def fetch_building_info(self):
        # Fetch the building information from the LBD model (OPT level 1)
        pass

    def create_textual_description(self, building_info):
        # Create a textual description from the fetched building information
        pass

    def process(self):
        building_info = self.fetch_building_info()
        description = self.create_textual_description(building_info)
        return description

if __name__ == "__main__":
    fetcher = BuildingInfoFetcher()
    description = fetcher.process()
    print(description)


class GenerateAnswer(dspy.Signature):
    """Answer questions with logical factoid answers."""

    context = dspy.InputField(desc="will contain an AI act related document")
    question = dspy.InputField()
    answer = dspy.OutputField(desc="an answer within 20 to 30 words")

class GraphSparqlQAChain:
    def __init__(self, model_name: str, graph_file: str):
        self.model_name = model_name
        self.graph_file = graph_file

    @staticmethod
    def from_llm(model_name: str, graph_file: str):
        return GraphSparqlQAChain(model_name, graph_file)

    def query_graph(self, question: str):

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
        results = self.query_graph(question)
        context_txt = " ".join([result["predicate"]+" "+result["object"] for result in results])

        lm = dspy.LM("ollama_chat/llama3.2",
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
