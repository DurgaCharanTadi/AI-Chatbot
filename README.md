# 🤖 AI Chatbot

**AI Chatbot** is an AI-powered web application for conversational Q&A with URL/file understanding and citations. Ask questions about documents or links you provide—or just chat to learn or have some fun. *(Note: the model doesn’t have real-time information.)*

---

## 🔗 Live Demo
🌐 https://www.durgacharantadi.com/ai-chatbot/

---

## 🧠 Features

- **✅ Conversational Q&A**   
  Drill down into complex documentation from pasted links or uploaded documents.

- **📎 URL & File Understanding with Citations**   
  Upload PDFs or share URLs; the app reads them and cites sources in its responses.

- **💬 Follow-Up & Learning Assistant**   
  Keep the chat going with follow-up questions to test yourself, learn deeply, or just ask for jokes.

---

## 🛠 Tech Stack

| Layer        | Technology                         |
|--------------|-------------------------------------|
| Frontend     | Angular                             |
| Backend      | Spring Boot                         |
| AI Services  | Claude 3 Sonnet (via Amazon Bedrock)|
| Hosting      | AWS S3 + CloudFront                 |
| Serverless   | AWS Lambda                          |

---

## ⚙️ How It Works

1. **User Input**:   
   Type a prompt, paste a URL, or upload a PDF.  
2. **Content Understanding**:   
   The app fetches and reads the provided content.  
3. **Answer Generation**:   
   Claude 3.5 Sonnet (via Bedrock) produces helpful responses with citations.  
4. **Results Displayed**:   
   Answers (with sources) appear in the chat interface.

---

## 📁 Project Structure

This repository contains both the frontend and backend code:

- `/frontend` — Angular application
- `/backend` — Spring Boot backend with Bedrock integration

---

## 🧠 Powered By
[Amazon Bedrock](https://aws.amazon.com/bedrock/)
**Claude 3.5 Sonnet** by Anthropic for answer generation

---

## 📄 License
This project uses AWS AI services and is intended for educational and portfolio purposes only.
