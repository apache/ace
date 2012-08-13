def libDir = new File("lib")
def output = new File('buildpath.txt')
output.text = ""

libDir.listFiles().each {
	output << "lib/${it.name};version=file,\\"
	output << '\n'
}

