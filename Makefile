.PHONY: build test clean verify-artifacts test-project-1 evaluate-project-1 benchmark-project-1 examples-project-1

GRADLE ?= ./gradlew
PROJECT_1 ?= ../messagevisor/projects/project-1

build:
	$(GRADLE) build

test:
	$(GRADLE) test

clean:
	$(GRADLE) clean

verify-artifacts:
	$(GRADLE) clean build generatePomFileForMavenJavaPublication
	bash scripts/verify-artifacts.sh

test-project-1:
	$(GRADLE) :cli:run --args='test --projectDirectoryPath=$(PROJECT_1) --onlyFailures --target=java --normalizeSpaces --withIcuModule --withInterpolationModule'

evaluate-project-1:
	$(GRADLE) :cli:run --args='evaluate --projectDirectoryPath=$(PROJECT_1) --locale=en-US --rawMessage=Hello --withIcuModule --json'

benchmark-project-1:
	$(GRADLE) :cli:run --args='benchmark --projectDirectoryPath=$(PROJECT_1) --locale=en-US --rawMessage=Hello --withIcuModule -n=1000 --json'

examples-project-1:
	$(GRADLE) --quiet :cli:run --args='examples --projectDirectoryPath=$(PROJECT_1) --withIcuModule'
