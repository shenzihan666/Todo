# Build a data analysis agent

Build an agent that analyzes data files, generates visualizations, and shares results.

This guide shows you how to build a data analysis agent using Deep Agents with a sandbox backend. The agent will:

1. Read data files (CSV, Excel, etc.)
2. Install and use data analysis libraries (pandas, numpy, matplotlib, seaborn)
3. Generate visualizations and statistical reports
4. Share results via Slack or other channels

## Example output

When you ask the agent to analyze sales data and share results to Slack, it will:

```
================================== Ai Message ==================================

[{'text': "I'll help you analyze the sales data and create a beautiful plot, then send the results to Slack.", 'type': 'text'}, {'id': 'toolu_01LRot5h6WkhdpDQ1SG6EQGQ', 'input': {'file_path': './data/sales_data.csv'}, 'name': 'read_file', 'type': 'tool_use'}]
```

The agent will:
- Explore the data structure
- Create a Python analysis script with pandas and matplotlib
- Run the analysis in the sandbox
- Generate a beautiful visualization dashboard
- Send the results to Slack

## Key capabilities demonstrated

1. **File exploration**: Uses `ls` and `read_file` to discover and read data
2. **Code generation**: Creates Python scripts with proper imports and analysis logic
3. **Package installation**: Automatically installs pandas, matplotlib, seaborn in the sandbox
4. **Execution**: Runs the analysis script using the `execute` tool
5. **Integration**: Sends results to external services like Slack

## What the agent does

The analysis typically includes:

### Data Overview
- Date range of the data
- Total records and products
- Summary statistics

### Statistical Analysis
- Total revenue and units sold
- Average daily revenue
- Product performance breakdown
- Best performing day/product

### Visualizations
- Daily revenue trends (bar chart)
- Daily units sold (bar chart)
- Revenue distribution by product (pie chart)
- Total revenue by product (horizontal bar chart)
- Total units sold by product (horizontal bar chart)
- Sales transactions distribution (pie chart)

## Use cases

This pattern is useful for:

- **Business intelligence**: Automated sales/financial reporting
- **Scientific research**: Analyzing experimental data
- **Marketing analytics**: Campaign performance analysis
- **Operations**: Supply chain and inventory analysis

## Architecture

The agent uses:

1. **Sandbox backend**: Isolated environment for code execution
2. **Filesystem tools**: For reading data and writing scripts
3. **Execute tool**: For running Python scripts and installing packages
4. **Custom tools**: For sending results to external services (e.g., Slack)

## Best practices

1. **Seed the sandbox**: Upload data files before running the agent
2. **Set expectations**: Specify output format (PNG, PDF, etc.)
3. **Handle errors**: Use try-except blocks in generated code
4. **Clean up**: Remove intermediate files after analysis
5. **Document**: Add comments and docstrings to generated code

---

*For full documentation, see https://docs.langchain.com/oss/python/deepagents/data-analysis*
