Embulk::JavaPlugin.register_parser(
  "xpath2", "org.embulk.parser.xpath2.XPath2ParserPlugin",
  File.expand_path('../../../../classpath', __FILE__))
