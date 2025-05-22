Travel Expense Chatbot

A JavaFX-based conversational chatbot that simplifies the process of completing German travel expense claim forms (Reisekostenformular). The application guides users through an interactive interview process and automatically generates a filled PDF form.

Features

Conversational Interface: Step-by-step guided form completion through a chatbot interface

Multilingual Support: Available in German and English

Input Validation: Real-time validation for dates, times, email addresses, phone numbers, IBAN, and BIC

PDF Generation: Automatically fills and generates official travel expense PDF forms

Smart Form Logic: Conditional questions based on user responses

Comprehensive Coverage: Handles all aspects of travel expense claims including:

Personal information

Travel details (dates, times, destinations)

Transportation methods and costs

Accommodation expenses

Additional costs and reimbursements


  
License

This project is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).

What this means:

-You can use, modify, and distribute this software freely

-All source code must remain open source

-Important: If you run this software as a web service, you must provide the source code to all users

-Any modifications or derivative works must also be licensed under AGPL-3.0

Why AGPL-3.0?

This project uses the iText library for PDF processing, which is licensed under AGPL-3.0. Therefore, the entire project must comply with AGPL-3.0 requirements.



Prerequisites

Java 11 or higher

JavaFX SDK

iText PDF library (AGPL-3.0)

Installation:

1. Clone the repository

2. Important: Use the provided rkinland.pdf template file included in this repository. The application's field mapping is specifically configured for this exact PDF version. Using a different version of the form may result in incorrect field placement or missing data in the generated PDF.

3. Compile and run the application

Usage

Language Selection: Choose between German and English interface

Interactive Interview: Answer questions step-by-step as prompted by the chatbot

Input Validation: The system validates your inputs in real-time and asks for corrections if needed

Form Completion: Complete all required sections

PDF Generation: Review your information and generate the completed PDF form

Architecture

The application follows a state-machine pattern with the following key components:

ChatbotState Enum: Defines all possible states in the conversation flow
Input Validation: Comprehensive validation for different data types
Internationalization: Resource bundles for German and English languages
PDF Integration: Direct form field mapping and custom text overlays using iText
JavaFX GUI: Responsive user interface

Field Mapping

The application uses a combination of:

Standard PDF form fields: For checkboxes, radio buttons, and basic text fields

Custom text overlays: For date/time fields and complex positioning requirements

Using a different PDF template will likely result in:

Misaligned text placement

Missing or incorrectly filled fields

Incomplete form generation


Important Disclaimers

Legal Notice

This software is provided for educational and research purposes as part of an academic thesis. It is NOT officially endorsed by German authorities or government agencies. Users are responsible for ensuring compliance with applicable laws and regulations.

Third-Party Dependencies

iText: Used for PDF processing, licensed under AGPL-3.0

JavaFX: User interface framework

Academic Context

This project was developed as part of a master's thesis on "Conception, Development and Testing of a Static Form-based Chatbot" and demonstrates practical applications of conversational interfaces in administrative processes.

Academic Citation

If you use this software in academic research, please cite:

[Arthur Jäger]. "Konzeption, Entwicklung und Test eines statischen formularbasierten Chatbots." 

[Universität der Bundeswehr München], [2025].

Support

If you encounter any issues or have questions:

Open an issue on GitHub

Check the existing documentation

Review the source code

License Text

Travel Expense Chatbot

Copyright (C) 2025 Arthur Jäger

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published
by the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
