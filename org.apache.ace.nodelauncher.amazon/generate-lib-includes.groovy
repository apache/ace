def libDir = new File("lib")
def output = new File('libs.txt')
output.text = ""

libDir.listFiles().each {
	output << "@lib/${it.name},\\"
	output << '\n'
}

