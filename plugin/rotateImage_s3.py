#!/usr/bin/env python3

import os, sys, tempfile

	config_file = "/opt/digiverso/goobi/config/goobi_config.properties"

def main():
	
	print("move image into backup")
	
	#get the file to local dist and rotate it
	dest_file = get_dest_file(sys.argv[1])
	s3_copy(sys.argv[1], dest_file)
	subprocess.call(["/usr/local/bin/mogrify", "-rotate", sys.argv[2], dest_file])
	s3_copy(dest_file, sys.argv[1])
	os.remove(dest_file)
	
	print("moving image finished")

def get_dest_file(s3_uri):
	return os.path.join(tempfile.gettempdir(), s3_uri[s3_uri.rfind("/")+1:])

def s3_copy(source, target):
	goobi_config = load_properties(config_file)
	s3_command = ["aws"]
	env = {}
	if "useCustomS3" in goobi_config and goobi_config["useCustomS3"].lower() == "true":
		s3_command.extend(["--endpoint-url", goobi_config["S3Endpoint"]])
		env["AWS_ACCESS_KEY_ID"] = goobi_config["S3AccessKeyID"]
		env["AWS_SECRET_ACCESS_KEY"] = goobi_config["S3SecretAccessKey"]
	
	cp_command = s3_command
	cp_command.extend(["cp", source, target])
	subprocess.call(cp_command, env=env)

def load_properties(filepath, sep='=', comment_char='#'):
    """
    Read the file passed as parameter as a properties file.
    """
    props = {}
    with open(filepath, "rt") as f:
        for line in f:
            l = line.strip()
            if l and not l.startswith(comment_char):
                key_value = l.split(sep)
                key = key_value[0].strip()
                value = sep.join(key_value[1:]).strip().strip('"') 
                props[key] = value 
    return props
    
if __name__ == "__main__":
    main()
